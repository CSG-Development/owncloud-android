package com.owncloud.android.workers

import android.accounts.Account
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.owncloud.android.R
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
import com.owncloud.android.lib.common.network.OnDatatransferProgressListener
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
    private lateinit var progress: ArchiveOperationProgress
    private var downloadBytesTotal = 0L
    private var completedDownloadBytes = 0L
    private var filesToDownloadCount = 0
    private var downloadedFilesCount = 0

    override suspend fun doWork(): Result {
        if (!areParametersValid()) return Result.failure()

        progress = ArchiveOperationProgress.forWorker(
            scope = CoroutineScope(coroutineContext),
            setProgress = { data -> setProgress(data) },
        )
        progress.reportStart()

        spaceWebDavUrl = getWebDavUrlForSpaceUseCase(
            GetWebDavUrlForSpaceUseCase.Params(accountName = account.name, spaceId = parentFolder.spaceId),
        )

        val archiveFileName = ArchiveNameResolver.resolveArchiveBaseName(
            selectedFiles = selectedFiles,
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

            val needsDownload = collectionResult.fileEntries.any { !it.ocFile.isAvailableLocally }
            progress.configurePhases(
                if (needsDownload) {
                    ZipPhase.DEFAULT_WEIGHTS
                } else {
                    ZipPhase.NO_DOWNLOAD_WEIGHTS
                },
            )
            progress.completePhase(ZipPhase.COLLECT)

            if (needsDownload) {
                val remoteEntries = collectionResult.fileEntries.filter { !it.ocFile.isAvailableLocally }
                filesToDownloadCount = remoteEntries.size
                downloadBytesTotal = if (remoteEntries.all { it.ocFile.length > 0L }) {
                    remoteEntries.sumOf { it.ocFile.length }
                } else {
                    0L
                }
                downloadedFilesCount = 0
                completedDownloadBytes = 0L
            }

            val localEntries = collectionResult.fileEntries.map { entry ->
                ArchiveEntryWithLocalPath(
                    zipEntryPath = entry.zipEntryPath,
                    localFile = resolveLocalFile(entry.ocFile),
                )
            }
            if (needsDownload) {
                progress.completePhase(ZipPhase.DOWNLOAD)
            }

            val tempZipFile = createTempZipFile(archiveFileName)
            ZipArchiveBuilder.build(
                fileEntries = localEntries,
                emptyDirectoryPaths = collectionResult.emptyDirectoryPaths,
                outputZipFile = tempZipFile,
                onBytesProcessed = { processed, total ->
                    if (total > 0L) {
                        progress.reportPhaseProgress(
                            ZipPhase.BUILD,
                            processed.toDouble() / total.toDouble(),
                        )
                    }
                },
            )
            progress.completePhase(ZipPhase.BUILD)
            ensureNotCancelled()

            val client = getClient()
            val remoteZipPath = getAvailableRemotePath(
                ownCloudClient = client,
                remotePath = ArchiveNameResolver.resolveRemoteZipPath(parentFolder, archiveFileName),
                spaceWebDavUrl = spaceWebDavUrl,
                isUserLogged = AccountUtils.getCurrentOwnCloudAccount(appContext) != null,
            )

            uploadZip(client, tempZipFile, remoteZipPath)
            progress.completePhase(ZipPhase.UPLOAD)
            ensureNotCancelled()

            fileRepository.refreshFolder(
                remotePath = parentFolder.remotePath,
                accountName = account.name,
                spaceId = parentFolder.spaceId,
            )

            progress.reportComplete()
            notifyZipResult(throwable = null, archiveFileName = archiveFileName)
        } catch (throwable: Throwable) {
            Timber.e(throwable, "Zip operation failed")
            notifyZipResult(throwable = throwable, archiveFileName = archiveFileName)
        } finally {
            cleanupTempFiles()
        }
    }

    private fun notifyZipResult(throwable: Throwable?, archiveFileName: String): Result {
        if (throwable !is CancelledException) {
            val contentTitle = if (throwable == null) {
                appContext.getString(R.string.homecloud_filelist_compress_succeeded_ticker)
            } else {
                appContext.getString(R.string.homecloud_filelist_compress_failed_ticker, archiveFileName)
            }

            val contentText = ErrorMessageAdapter.getMessageFromArchiveOperation(
                isCompress = true,
                displayName = archiveFileName,
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

        val downloadFolderName = archiveZipSourcesFolderName()
        val temporalFolderPath = FileStorageUtils.getTemporalPath(account.name, ocFile.spaceId)
        val tempDownloadPath = File(
            temporalFolderPath,
            "$downloadFolderName${ocFile.remotePath}",
        )
        tempDownloadPath.parentFile?.mkdirs()
        registerForCleanup(tempDownloadPath)

        val downloadOperation = DownloadRemoteFileOperation(
            remotePath = ocFile.remotePath,
            localFolderPath = "$temporalFolderPath/$downloadFolderName",
            spaceWebDavUrl = spaceWebDavUrl,
        )
        val progressListener = OnDatatransferProgressListener { _, totalTransferredSoFar, totalToTransfer, _ ->
            reportDownloadProgress(
                ocFile = ocFile,
                currentFileTransferred = totalTransferredSoFar,
                currentFileTotal = resolveTransferTotal(
                    reportedTotal = totalToTransfer,
                    knownFileSize = ocFile.length,
                ),
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
                IllegalStateException("Downloaded file not found at ${tempDownloadPath.absolutePath}"),
            )
        }
        onDownloadFileComplete(ocFile, tempDownloadPath)
        return tempDownloadPath
    }

    private fun reportDownloadProgress(
        ocFile: OCFile,
        currentFileTransferred: Long,
        currentFileTotal: Long,
    ) {
        val fileTotal = currentFileTotal.coerceAtLeast(1L)
        if (downloadBytesTotal > 0L) {
            val aggregateTransferred = (completedDownloadBytes + currentFileTransferred.coerceAtMost(fileTotal))
                .coerceAtMost(downloadBytesTotal)
            progress.reportPhaseProgress(
                ZipPhase.DOWNLOAD,
                aggregateTransferred.toDouble() / downloadBytesTotal.toDouble(),
            )
        } else if (filesToDownloadCount > 0) {
            val fileFraction = currentFileTransferred.toDouble() / fileTotal
            progress.reportPhaseProgress(
                ZipPhase.DOWNLOAD,
                (downloadedFilesCount + fileFraction) / filesToDownloadCount.toDouble(),
            )
        }
    }

    private fun onDownloadFileComplete(ocFile: OCFile, downloadedFile: File) {
        downloadedFilesCount++
        completedDownloadBytes += when {
            ocFile.length > 0L -> ocFile.length
            else -> downloadedFile.length()
        }
        reportDownloadAggregateProgress()
    }

    private fun reportDownloadAggregateProgress() {
        if (downloadBytesTotal > 0L) {
            progress.reportPhaseProgress(
                ZipPhase.DOWNLOAD,
                completedDownloadBytes.toDouble() / downloadBytesTotal.toDouble(),
            )
        } else if (filesToDownloadCount > 0) {
            progress.reportPhaseProgress(
                ZipPhase.DOWNLOAD,
                downloadedFilesCount.toDouble() / filesToDownloadCount.toDouble(),
            )
        }
    }

    private fun resolveTransferTotal(reportedTotal: Long, knownFileSize: Long): Long =
        when {
            reportedTotal > 0L -> reportedTotal
            knownFileSize > 0L -> knownFileSize
            else -> 1L
        }

    private fun uploadZip(client: OwnCloudClient, zipFile: File, remotePath: String) {
        if (FileStorageUtils.getUsableSpace() < zipFile.length()) {
            throw LocalStorageFullException()
        }

        val uploadOperation = UploadFileFromFileSystemOperation(
            localPath = zipFile.absolutePath,
            remotePath = remotePath,
            mimeType = ArchiveMimeTypes.ZIP,
            lastModifiedTimestamp = (zipFile.lastModified() / 1_000).toString(),
            requiredEtag = null,
            spaceWebDavUrl = spaceWebDavUrl,
        )
        val progressListener = OnDatatransferProgressListener { _, totalTransferredSoFar, totalToTransfer, _ ->
            if (totalToTransfer <= 0L) return@OnDatatransferProgressListener
            progress.reportPhaseProgress(
                ZipPhase.UPLOAD,
                totalTransferredSoFar.toDouble() / totalToTransfer.toDouble(),
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
    }

    private fun archiveZipSourcesFolderName(): String =
        "archive_zip_sources_${workerParameters.id}"

    private fun createTempZipFile(fileName: String): File {
        val temporalFolderPath = FileStorageUtils.getTemporalPath(account.name, parentFolder.spaceId)
        val tempFile = File(temporalFolderPath, "zip_output_${workerParameters.id}/$fileName")
        tempFile.parentFile?.mkdirs()
        registerForCleanup(tempFile)
        return tempFile
    }

    private fun registerForCleanup(file: File) {
        tempPathsToCleanup.add(file)
        file.parentFile?.let { parent ->
            val parentName = parent.name
            if (parentName.startsWith("archive_zip_sources") || parentName.startsWith("zip_output")) {
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
        const val KEY_PARAM_PARENT_FOLDER_ID = "KEY_PARAM_PARENT_FOLDER_ID"
        const val KEY_PARAM_FILE_IDS = "KEY_PARAM_FILE_IDS"
        private const val ARCHIVE_NOTIFICATION_ID = 14
    }
}
