package com.owncloud.android.workers

import android.accounts.Account
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.owncloud.android.R
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
import com.owncloud.android.lib.common.network.OnDatatransferProgressListener
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import com.owncloud.android.lib.resources.files.CheckPathExistenceRemoteOperation
import com.owncloud.android.lib.resources.files.CreateRemoteFolderOperation
import com.owncloud.android.lib.resources.files.DownloadRemoteFileOperation
import com.owncloud.android.lib.resources.files.UploadFileFromFileSystemOperation
import com.owncloud.android.presentation.authentication.AccountUtils
import com.owncloud.android.ui.errorhandling.ErrorMessageAdapter
import com.owncloud.android.utils.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import com.owncloud.android.utils.FileStorageUtils
import com.owncloud.android.utils.NOTIFICATION_TIMEOUT_STANDARD
import com.owncloud.android.utils.NotificationUtils.createBasicNotification
import com.owncloud.android.utils.RemoteFileUtils.getAvailableRemotePath
import kotlinx.coroutines.CoroutineScope
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
    private lateinit var progress: ArchiveOperationProgress
    private var targetRemotePath: String? = null
    private var lastProgressPercent = 0
    private var uploadBytesTotal = 0L
    private var uploadedBytes = 0L
    private var filesToUploadCount = 0
    private var uploadedFilesCount = 0

    override suspend fun doWork(): Result {
        if (!areParametersValid()) return Result.failure()

        targetRemotePath = readPersistedTargetRemotePath()

        progress = ArchiveOperationProgress.forWorker(
            scope = CoroutineScope(coroutineContext),
            setProgress = { data ->
                lastProgressPercent = data.getInt(DownloadFileWorker.WORKER_KEY_PROGRESS, lastProgressPercent)
                setProgress(mergeTargetPathIntoProgress(data))
            },
        )
        progress.reportStart()

        spaceWebDavUrl = getWebDavUrlForSpaceUseCase(
            GetWebDavUrlForSpaceUseCase.Params(accountName = account.name, spaceId = zipFile.spaceId),
        )

        val zipFileName = zipFile.fileName
        val needsDownload = !zipFile.isAvailableLocally
        progress.configurePhases(
            if (needsDownload) {
                UnzipPhase.DEFAULT_WEIGHTS
            } else {
                UnzipPhase.NO_DOWNLOAD_WEIGHTS
            },
        )

        return try {
            ensureNotCancelled()
            val localZipFile = resolveLocalZipFile()
            if (needsDownload) {
                progress.completePhase(UnzipPhase.DOWNLOAD)
            }

            val extractDirectory = createExtractDirectory()
            ZipArchiveExtractor.extract(
                zipFile = localZipFile,
                targetDirectory = extractDirectory,
                onBytesProcessed = { processed, total ->
                    if (total > 0L) {
                        progress.reportPhaseProgress(
                            UnzipPhase.EXTRACT,
                            processed.toDouble() / total.toDouble(),
                        )
                    }
                },
            )
            progress.completePhase(UnzipPhase.EXTRACT)
            ensureNotCancelled()

            val client = getClient()
            val targetSubfolderPath = resolveTargetSubfolderPath(client)

            createRemoteFolder(client, targetSubfolderPath)
            progress.completePhase(UnzipPhase.CREATE_FOLDER)

            uploadBytesTotal = sumFileSizesRecursively(extractDirectory)
            filesToUploadCount = countFilesRecursively(extractDirectory)
            uploadedBytes = 0L
            uploadedFilesCount = 0
            uploadDirectoryRecursively(
                client = client,
                localDirectory = extractDirectory,
                remoteBasePath = targetSubfolderPath,
            )
            progress.completePhase(UnzipPhase.UPLOAD)
            ensureNotCancelled()

            fileRepository.refreshFolder(
                remotePath = zipFile.getParentRemotePath(),
                accountName = account.name,
                spaceId = zipFile.spaceId,
            )

            progress.reportComplete()
            clearPersistedTargetRemotePath()
            notifyUnzipResult(throwable = null, zipFileName = zipFileName)
        } catch (throwable: Throwable) {
            Timber.e(throwable, "Unzip operation failed")
            notifyUnzipResult(throwable = throwable, zipFileName = zipFileName)
        } finally {
            cleanupTempFiles()
        }
    }

    private fun notifyUnzipResult(throwable: Throwable?, zipFileName: String): Result {
        if (throwable !is CancelledException) {
            val contentTitle = if (throwable == null) {
                appContext.getString(R.string.homecloud_filelist_extract_succeeded_ticker)
            } else {
                appContext.getString(R.string.homecloud_filelist_extract_failed_ticker, zipFileName)
            }

            val contentText = ErrorMessageAdapter.getMessageFromArchiveOperation(
                isCompress = false,
                displayName = zipFileName,
                throwable = throwable,
                resources = appContext.resources,
            )

            val timeOut = if (throwable == null) NOTIFICATION_TIMEOUT_STANDARD else null

            createBasicNotification(
                context = appContext,
                contentTitle = contentTitle,
                notificationChannelId = DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                notificationId = ARCHIVE_NOTIFICATION_ID,
                intent = null,
                contentText = contentText,
                timeOut = timeOut,
            )
        }

        return when {
            throwable == null -> Result.success()
            throwable is NoNetworkConnectionException -> Result.retry()
            else -> Result.failure()
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

        val downloadFolderName = archiveUnzipSourceFolderName()
        val temporalFolderPath = FileStorageUtils.getTemporalPath(account.name, zipFile.spaceId)
        val tempDownloadPath = File(temporalFolderPath, "$downloadFolderName${zipFile.remotePath}")
        tempDownloadPath.parentFile?.mkdirs()
        registerForCleanup(tempDownloadPath)

        val downloadOperation = DownloadRemoteFileOperation(
            remotePath = zipFile.remotePath,
            localFolderPath = "$temporalFolderPath/$downloadFolderName",
            spaceWebDavUrl = spaceWebDavUrl,
        )
        val progressListener = OnDatatransferProgressListener { _, totalTransferredSoFar, totalToTransfer, _ ->
            val fileTotal = resolveTransferTotal(
                reportedTotal = totalToTransfer,
                knownFileSize = zipFile.length,
            )
            progress.reportPhaseProgress(
                UnzipPhase.DOWNLOAD,
                totalTransferredSoFar.toDouble() / fileTotal.toDouble(),
            )
        }
        downloadOperation.addDatatransferProgressListener(progressListener)
        try {
            executeRemoteOperation {
                downloadOperation.execute(getClient())
            }
        } finally {
            downloadOperation.removeDatatransferProgressListener(progressListener)
        }

        if (!tempDownloadPath.exists()) {
            throw InvalidArchiveException(
                IllegalStateException("Downloaded zip not found at ${tempDownloadPath.absolutePath}"),
            )
        }
        return tempDownloadPath
    }

    private suspend fun resolveTargetSubfolderPath(client: OwnCloudClient): String {
        targetRemotePath?.let { return it }

        val resolvedPath = getAvailableRemotePath(
            ownCloudClient = client,
            remotePath = ArchiveNameResolver.resolveExtractSubfolderPath(zipFile)
                .trimEnd(OCFile.PATH_SEPARATOR),
            spaceWebDavUrl = spaceWebDavUrl,
            isUserLogged = AccountUtils.getCurrentOwnCloudAccount(appContext) != null,
        ) + OCFile.PATH_SEPARATOR

        val normalizedPath = normalizeRemoteFolderPath(resolvedPath)
        targetRemotePath = normalizedPath
        persistTargetRemotePath(normalizedPath)
        setProgress(
            mergeTargetPathIntoProgress(
                workDataOf(DownloadFileWorker.WORKER_KEY_PROGRESS to lastProgressPercent),
            ),
        )
        return normalizedPath
    }

    private fun targetPathPersistenceFile(): File {
        val temporalFolderPath = FileStorageUtils.getTemporalPath(account.name, zipFile.spaceId)
        return File(temporalFolderPath, "unzip_target_${workerParameters.id}")
    }

    private fun readPersistedTargetRemotePath(): String? {
        val file = targetPathPersistenceFile()
        if (!file.exists()) return null
        return file.readText()
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let(::normalizeRemoteFolderPath)
    }

    private fun persistTargetRemotePath(path: String) {
        val file = targetPathPersistenceFile()
        file.parentFile?.mkdirs()
        file.writeText(path)
    }

    private fun clearPersistedTargetRemotePath() {
        val file = targetPathPersistenceFile()
        if (file.exists() && !file.delete()) {
            Timber.w("Failed to delete persisted target path: ${file.absolutePath}")
        }
    }

    private fun mergeTargetPathIntoProgress(data: Data): Data {
        val targetPath = targetRemotePath ?: return data
        return Data.Builder()
            .putAll(data)
            .putString(KEY_TARGET_REMOTE_PATH, targetPath)
            .build()
    }

    private fun normalizeRemoteFolderPath(path: String): String =
        if (path.endsWith(OCFile.PATH_SEPARATOR)) {
            path
        } else {
            "$path${OCFile.PATH_SEPARATOR}"
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

    private fun countFilesRecursively(directory: File): Int =
        directory.listFiles().orEmpty().sumOf { child ->
            if (child.isDirectory) countFilesRecursively(child) else 1
        }

    private fun sumFileSizesRecursively(directory: File): Long =
        directory.listFiles().orEmpty().sumOf { child ->
            if (child.isDirectory) sumFileSizesRecursively(child) else child.length()
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

        val fileSize = localFile.length()
        val uploadOperation = UploadFileFromFileSystemOperation(
            localPath = localFile.absolutePath,
            remotePath = remotePath,
            mimeType = FileStorageUtils.getMimeTypeFromName(localFile.absolutePath),
            lastModifiedTimestamp = (localFile.lastModified() / 1_000).toString(),
            requiredEtag = null,
            spaceWebDavUrl = spaceWebDavUrl,
        )
        val progressListener = OnDatatransferProgressListener { _, totalTransferredSoFar, totalToTransfer, _ ->
            reportUploadProgress(
                currentFileTransferred = totalTransferredSoFar,
                currentFileTotal = resolveTransferTotal(
                    reportedTotal = totalToTransfer,
                    knownFileSize = fileSize,
                ),
            )
        }
        uploadOperation.addDataTransferProgressListener(progressListener)
        try {
            executeRemoteOperation {
                uploadOperation.execute(client)
            }
        } finally {
            uploadOperation.removeDataTransferProgressListener(progressListener)
        }
        onUploadFileComplete(fileSize)
    }

    private fun reportUploadProgress(
        currentFileTransferred: Long,
        currentFileTotal: Long,
    ) {
        val fileTotal = currentFileTotal.coerceAtLeast(1L)
        if (uploadBytesTotal > 0L) {
            val aggregateTransferred = (uploadedBytes + currentFileTransferred.coerceAtMost(fileTotal))
                .coerceAtMost(uploadBytesTotal)
            progress.reportPhaseProgress(
                UnzipPhase.UPLOAD,
                aggregateTransferred.toDouble() / uploadBytesTotal.toDouble(),
            )
        } else if (filesToUploadCount > 0) {
            val fileFraction = currentFileTransferred.toDouble() / fileTotal
            progress.reportPhaseProgress(
                UnzipPhase.UPLOAD,
                (uploadedFilesCount + fileFraction) / filesToUploadCount.toDouble(),
            )
        }
    }

    private fun onUploadFileComplete(fileSize: Long) {
        uploadedFilesCount++
        uploadedBytes += fileSize
        reportUploadAggregateProgress()
    }

    private fun reportUploadAggregateProgress() {
        if (uploadBytesTotal > 0L) {
            progress.reportPhaseProgress(
                UnzipPhase.UPLOAD,
                uploadedBytes.toDouble() / uploadBytesTotal.toDouble(),
            )
        } else if (filesToUploadCount > 0) {
            progress.reportPhaseProgress(
                UnzipPhase.UPLOAD,
                uploadedFilesCount.toDouble() / filesToUploadCount.toDouble(),
            )
        }
    }

    private fun resolveTransferTotal(reportedTotal: Long, knownFileSize: Long): Long =
        when {
            reportedTotal > 0L -> reportedTotal
            knownFileSize > 0L -> knownFileSize
            else -> 1L
        }

    private fun archiveUnzipSourceFolderName(): String =
        "archive_unzip_source_${workerParameters.id}"

    private fun createExtractDirectory(): File {
        val temporalFolderPath = FileStorageUtils.getTemporalPath(account.name, zipFile.spaceId)
        val tempDirectory = File(temporalFolderPath, "unzip_output_${workerParameters.id}")
        if (tempDirectory.exists()) {
            val cleared = FileStorageUtils.deleteDir(tempDirectory)
            if (!cleared) {
                Timber.w("Failed to clear extract directory ${tempDirectory.absolutePath}")
            }
        }
        tempDirectory.mkdirs()
        registerForCleanup(tempDirectory)
        return tempDirectory
    }

    private fun registerForCleanup(file: File) {
        tempPathsToCleanup.add(file)
        file.parentFile?.let { parent ->
            val parentName = parent.name
            if (parentName.startsWith("archive_unzip_source") || parentName.startsWith("unzip_output")) {
                tempPathsToCleanup.add(parent)
            }
        }
    }

    private fun cleanupTempFiles() {
        tempPathsToCleanup
            .distinctBy { it.absolutePath }
            .sortedByDescending { it.absolutePath.length }
            .forEach { file ->
                val deleted = when {
                    file.isDirectory -> FileStorageUtils.deleteDir(file)
                    file.exists() -> file.delete()
                    else -> true
                }
                if (!deleted) {
                    Timber.w("Failed to delete temp file: ${file.absolutePath}")
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
        const val KEY_TARGET_REMOTE_PATH = "KEY_TARGET_REMOTE_PATH"
        private const val ARCHIVE_NOTIFICATION_ID = 15
    }
}
