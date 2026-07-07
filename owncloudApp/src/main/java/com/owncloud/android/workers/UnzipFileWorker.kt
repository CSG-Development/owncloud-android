package com.owncloud.android.workers

import android.accounts.Account
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.owncloud.android.data.executeRemoteOperation
import com.owncloud.android.domain.archive.ArchiveMimeTypes
import com.owncloud.android.domain.archive.ArchiveNameResolver
import com.owncloud.android.domain.archive.ZipArchiveExtractor
import com.owncloud.android.domain.exceptions.CancelledException
import com.owncloud.android.domain.exceptions.InvalidArchiveException
import com.owncloud.android.domain.exceptions.LocalStorageFullException
import com.owncloud.android.domain.exceptions.NoNetworkConnectionException
import com.owncloud.android.domain.files.FileRepository
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.usecases.GetFileByIdUseCase
import com.owncloud.android.domain.files.usecases.GetWebDavUrlForSpaceUseCase
import com.owncloud.android.lib.common.OwnCloudAccount
import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.SingleSessionManager
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import com.owncloud.android.lib.resources.files.CheckPathExistenceRemoteOperation
import com.owncloud.android.lib.resources.files.CreateRemoteFolderOperation
import com.owncloud.android.lib.resources.files.DownloadRemoteFileOperation
import com.owncloud.android.lib.resources.files.UploadFileFromFileSystemOperation
import com.owncloud.android.presentation.authentication.AccountUtils
import com.owncloud.android.utils.FileStorageUtils
import com.owncloud.android.utils.RemoteFileUtils.getAvailableRemotePath
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File

class UnzipFileWorker(
    private val appContext: Context,
    private val workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters), KoinComponent {

    private val getFileByIdUseCase: GetFileByIdUseCase by inject()
    private val getWebDavUrlForSpaceUseCase: GetWebDavUrlForSpaceUseCase by inject()
    private val fileRepository: FileRepository by inject()

    private lateinit var account: Account
    private lateinit var zipFile: OCFile
    private var spaceWebDavUrl: String? = null

    private val tempPathsToCleanup = mutableListOf<File>()

    override suspend fun doWork(): Result {
        if (!areParametersValid()) return Result.failure()

        spaceWebDavUrl = getWebDavUrlForSpaceUseCase(
            GetWebDavUrlForSpaceUseCase.Params(accountName = account.name, spaceId = zipFile.spaceId),
        )

        return try {
            ensureNotCancelled()
            val localZipFile = resolveLocalZipFile()
            val extractDirectory = createTempDirectory("unzip_output")
            ZipArchiveExtractor.extract(localZipFile, extractDirectory)
            ensureNotCancelled()

            val client = getClient()
            val targetSubfolderPath = getAvailableRemotePath(
                ownCloudClient = client,
                remotePath = ArchiveNameResolver.resolveExtractSubfolderPath(zipFile),
                spaceWebDavUrl = spaceWebDavUrl,
                isUserLogged = AccountUtils.getCurrentOwnCloudAccount(appContext) != null,
            )

            createRemoteFolder(client, targetSubfolderPath)
            uploadDirectoryRecursively(
                client = client,
                localDirectory = extractDirectory,
                remoteBasePath = targetSubfolderPath,
            )
            ensureNotCancelled()

            fileRepository.refreshFolder(
                remotePath = zipFile.getParentRemotePath(),
                accountName = account.name,
                spaceId = zipFile.spaceId,
            )

            Result.success()
        } catch (throwable: Throwable) {
            Timber.e(throwable, "Unzip operation failed")
            when (throwable) {
                is NoNetworkConnectionException -> Result.retry()
                is CancelledException -> Result.failure()
                else -> Result.failure()
            }
        } finally {
            cleanupTempFiles()
        }
    }

    private fun areParametersValid(): Boolean {
        val accountName = workerParameters.inputData.getString(KEY_PARAM_ACCOUNT) ?: return false
        val zipFileId = workerParameters.inputData.getLong(KEY_PARAM_ZIP_FILE_ID, -1)

        account = AccountUtils.getOwnCloudAccountByName(appContext, accountName) ?: return false
        zipFile = getFileByIdUseCase(GetFileByIdUseCase.Params(zipFileId)).getDataOrNull()
            ?.takeIf { !it.isFolder && ArchiveMimeTypes.isZipFile(it) } ?: return false

        return true
    }

    private fun resolveLocalZipFile(): File {
        if (zipFile.isAvailableLocally) {
            return File(zipFile.storagePath!!)
        }

        val temporalFolderPath = FileStorageUtils.getTemporalPath(account.name, zipFile.spaceId)
        val tempDownloadPath = File(temporalFolderPath, "archive_unzip_source${zipFile.remotePath}")
        tempDownloadPath.parentFile?.mkdirs()
        registerForCleanup(tempDownloadPath)

        val downloadOperation = DownloadRemoteFileOperation(
            remotePath = zipFile.remotePath,
            localFolderPath = "$temporalFolderPath/archive_unzip_source",
            spaceWebDavUrl = spaceWebDavUrl,
        )
        executeRemoteOperation {
            downloadOperation.execute(getClient())
        }

        if (!tempDownloadPath.exists()) {
            throw InvalidArchiveException(
                IllegalStateException("Downloaded zip not found at ${tempDownloadPath.absolutePath}"),
            )
        }
        return tempDownloadPath
    }

    private fun createRemoteFolder(client: OwnCloudClient, remotePath: String) {
        val normalizedPath = if (remotePath.endsWith(OCFile.PATH_SEPARATOR)) {
            remotePath
        } else {
            "$remotePath${OCFile.PATH_SEPARATOR}"
        }

        val checkPathExistenceOperation = CheckPathExistenceRemoteOperation(
            remotePath = normalizedPath,
            isUserLoggedIn = AccountUtils.getCurrentOwnCloudAccount(appContext) != null,
            spaceWebDavUrl = spaceWebDavUrl,
        )
        val checkPathExistenceResult = checkPathExistenceOperation.execute(client)
        if (checkPathExistenceResult.isSuccess) {
            return
        }
        if (checkPathExistenceResult.code != ResultCode.FILE_NOT_FOUND) {
            executeRemoteOperation { checkPathExistenceResult }
        }

        val createRemoteFolderOperation = CreateRemoteFolderOperation(
            remotePath = normalizedPath,
            createFullPath = true,
            spaceWebDavUrl = spaceWebDavUrl,
        )
        executeRemoteOperation {
            createRemoteFolderOperation.execute(client)
        }
    }

    private fun uploadDirectoryRecursively(
        client: OwnCloudClient,
        localDirectory: File,
        remoteBasePath: String,
    ) {
        val children = localDirectory.listFiles().orEmpty().sortedWith(compareBy({ !it.isDirectory }, { it.name }))
        children.forEach { child ->
            ensureNotCancelled()
            val remotePath = remoteBasePath + child.name + if (child.isDirectory) OCFile.PATH_SEPARATOR else ""
            if (child.isDirectory) {
                createRemoteFolder(client, remotePath)
                uploadDirectoryRecursively(
                    client = client,
                    localDirectory = child,
                    remoteBasePath = remotePath,
                )
            } else {
                uploadFile(client, child, remotePath)
            }
        }
    }

    private fun uploadFile(client: OwnCloudClient, localFile: File, remotePath: String) {
        if (FileStorageUtils.getUsableSpace() < localFile.length()) {
            throw LocalStorageFullException()
        }

        val uploadOperation = UploadFileFromFileSystemOperation(
            localPath = localFile.absolutePath,
            remotePath = remotePath,
            mimeType = FileStorageUtils.getMimeTypeFromName(localFile.absolutePath),
            lastModifiedTimestamp = localFile.lastModified().toString(),
            requiredEtag = null,
            spaceWebDavUrl = spaceWebDavUrl,
        )
        executeRemoteOperation {
            uploadOperation.execute(client)
        }
    }

    private fun createTempDirectory(directoryName: String): File {
        val temporalFolderPath = FileStorageUtils.getTemporalPath(account.name, zipFile.spaceId)
        val tempDirectory = File(temporalFolderPath, directoryName)
        tempDirectory.mkdirs()
        registerForCleanup(tempDirectory)
        return tempDirectory
    }

    private fun registerForCleanup(file: File) {
        tempPathsToCleanup.add(file)
    }

    private fun cleanupTempFiles() {
        tempPathsToCleanup
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.absolutePath.length }
            .forEach { file ->
                if (file.isDirectory) {
                    FileStorageUtils.deleteDir(file)
                } else if (file.exists()) {
                    file.delete()
                }
            }
    }

    private fun getClient(): OwnCloudClient =
        SingleSessionManager.getDefaultSingleton().getClientFor(
            OwnCloudAccount(account, appContext),
            appContext,
        )

    private fun ensureNotCancelled() {
        if (isStopped) {
            throw CancelledException()
        }
    }

    companion object {
        const val KEY_PARAM_ACCOUNT = "KEY_PARAM_ACCOUNT"
        const val KEY_PARAM_ZIP_FILE_ID = "KEY_PARAM_ZIP_FILE_ID"
    }
}
