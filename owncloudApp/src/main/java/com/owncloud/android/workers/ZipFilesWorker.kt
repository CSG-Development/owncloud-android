package com.owncloud.android.workers

import android.accounts.Account
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.owncloud.android.data.executeRemoteOperation
import com.owncloud.android.domain.UseCaseResult
import com.owncloud.android.domain.archive.ArchiveEntryWithLocalPath
import com.owncloud.android.domain.archive.ArchiveMimeTypes
import com.owncloud.android.domain.archive.ArchiveNameResolver
import com.owncloud.android.domain.archive.ZipArchiveBuilder
import com.owncloud.android.domain.archive.usecases.CollectArchiveFilesUseCase
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
import com.owncloud.android.lib.resources.files.DownloadRemoteFileOperation
import com.owncloud.android.lib.resources.files.UploadFileFromFileSystemOperation
import com.owncloud.android.presentation.authentication.AccountUtils
import com.owncloud.android.utils.FileStorageUtils
import com.owncloud.android.utils.RemoteFileUtils.getAvailableRemotePath
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File

class ZipFilesWorker(
    private val appContext: Context,
    private val workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters), KoinComponent {

    private val getFileByIdUseCase: GetFileByIdUseCase by inject()
    private val getWebDavUrlForSpaceUseCase: GetWebDavUrlForSpaceUseCase by inject()
    private val fileRepository: FileRepository by inject()
    private val collectArchiveFilesUseCase: CollectArchiveFilesUseCase by inject()

    private lateinit var account: Account
    private lateinit var parentFolder: OCFile
    private lateinit var selectedFiles: List<OCFile>
    private var spaceWebDavUrl: String? = null

    private val tempPathsToCleanup = mutableListOf<File>()

    override suspend fun doWork(): Result {
        if (!areParametersValid()) return Result.failure()

        spaceWebDavUrl = getWebDavUrlForSpaceUseCase(
            GetWebDavUrlForSpaceUseCase.Params(accountName = account.name, spaceId = parentFolder.spaceId),
        )

        return try {
            ensureNotCancelled()
            val collectionResult = when (
                val result = collectArchiveFilesUseCase(
                    CollectArchiveFilesUseCase.Params(
                        selectedFiles = selectedFiles,
                        accountName = account.name,
                    ),
                )
            ) {
                is UseCaseResult.Success -> result.data
                is UseCaseResult.Error -> throw result.throwable
            }
            ensureNotCancelled()

            val localEntries = collectionResult.fileEntries.map { entry ->
                ArchiveEntryWithLocalPath(
                    zipEntryPath = entry.zipEntryPath,
                    localFile = resolveLocalFile(entry.ocFile),
                )
            }

            val archiveFileName = ArchiveNameResolver.resolveArchiveBaseName(
                selectedFiles = selectedFiles,
                parentFolder = parentFolder,
            )
            val tempZipFile = createTempFile("zip_output", archiveFileName)
            ZipArchiveBuilder.build(
                fileEntries = localEntries,
                emptyDirectoryPaths = collectionResult.emptyDirectoryPaths,
                outputZipFile = tempZipFile,
            )
            ensureNotCancelled()

            val client = getClient()
            val remoteZipPath = getAvailableRemotePath(
                ownCloudClient = client,
                remotePath = ArchiveNameResolver.resolveRemoteZipPath(parentFolder, archiveFileName),
                spaceWebDavUrl = spaceWebDavUrl,
                isUserLogged = AccountUtils.getCurrentOwnCloudAccount(appContext) != null,
            )

            uploadZip(client, tempZipFile, remoteZipPath)
            ensureNotCancelled()

            fileRepository.refreshFolder(
                remotePath = parentFolder.remotePath,
                accountName = account.name,
                spaceId = parentFolder.spaceId,
            )

            Result.success()
        } catch (throwable: Throwable) {
            Timber.e(throwable, "Zip operation failed")
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
        val parentFolderId = workerParameters.inputData.getLong(KEY_PARAM_PARENT_FOLDER_ID, -1)
        val fileIds = workerParameters.inputData.getLongArray(KEY_PARAM_FILE_IDS)?.toList() ?: return false

        account = AccountUtils.getOwnCloudAccountByName(appContext, accountName) ?: return false
        parentFolder = getFileByIdUseCase(GetFileByIdUseCase.Params(parentFolderId)).getDataOrNull()
            ?.takeIf { it.isFolder } ?: return false

        selectedFiles = fileIds.mapNotNull { fileId ->
            getFileByIdUseCase(GetFileByIdUseCase.Params(fileId)).getDataOrNull()
        }

        return selectedFiles.isNotEmpty() && selectedFiles.size == fileIds.size
    }

    private fun resolveLocalFile(ocFile: OCFile): File {
        if (ocFile.isAvailableLocally) {
            return File(ocFile.storagePath!!)
        }

        val temporalFolderPath = FileStorageUtils.getTemporalPath(account.name, ocFile.spaceId)
        val tempDownloadPath = File(
            temporalFolderPath,
            "archive_zip_sources${ocFile.remotePath}",
        )
        tempDownloadPath.parentFile?.mkdirs()
        registerForCleanup(tempDownloadPath)

        val downloadOperation = DownloadRemoteFileOperation(
            remotePath = ocFile.remotePath,
            localFolderPath = temporalFolderPath + "/archive_zip_sources",
            spaceWebDavUrl = spaceWebDavUrl,
        )
        executeRemoteOperation {
            downloadOperation.execute(getClient())
        }

        if (!tempDownloadPath.exists()) {
            throw InvalidArchiveException(
                IllegalStateException("Downloaded file not found at ${tempDownloadPath.absolutePath}"),
            )
        }
        return tempDownloadPath
    }

    private fun uploadZip(client: OwnCloudClient, zipFile: File, remotePath: String) {
        if (FileStorageUtils.getUsableSpace() < zipFile.length()) {
            throw LocalStorageFullException()
        }

        val uploadOperation = UploadFileFromFileSystemOperation(
            localPath = zipFile.absolutePath,
            remotePath = remotePath,
            mimeType = ArchiveMimeTypes.ZIP,
            lastModifiedTimestamp = zipFile.lastModified().toString(),
            requiredEtag = null,
            spaceWebDavUrl = spaceWebDavUrl,
        )
        executeRemoteOperation {
            uploadOperation.execute(client)
        }
    }

    private fun createTempFile(directoryName: String, fileName: String): File {
        val temporalFolderPath = FileStorageUtils.getTemporalPath(account.name, parentFolder.spaceId)
        val tempFile = File(temporalFolderPath, "$directoryName/$fileName")
        tempFile.parentFile?.mkdirs()
        registerForCleanup(tempFile)
        return tempFile
    }

    private fun registerForCleanup(file: File) {
        tempPathsToCleanup.add(file)
        file.parentFile?.let { parent ->
            if (parent.name == "archive_zip_sources" || parent.name == "zip_output") {
                tempPathsToCleanup.add(parent)
            }
        }
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
        const val KEY_PARAM_PARENT_FOLDER_ID = "KEY_PARAM_PARENT_FOLDER_ID"
        const val KEY_PARAM_FILE_IDS = "KEY_PARAM_FILE_IDS"
    }
}
