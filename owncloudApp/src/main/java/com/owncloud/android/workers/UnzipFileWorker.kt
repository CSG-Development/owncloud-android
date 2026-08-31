package com.owncloud.android.workers

import android.accounts.Account
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.owncloud.android.R
import com.owncloud.android.data.executeRemoteOperation
import com.owncloud.android.domain.archive.ArchiveExtractLayout
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
import com.owncloud.android.presentation.files.operations.ArchiveFailureClassifier
import com.owncloud.android.ui.errorhandling.ErrorMessageAdapter
import com.owncloud.android.usecases.archive.KEY_ARCHIVE_FAILURE_TYPE
import com.owncloud.android.utils.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import com.owncloud.android.utils.FileStorageUtils
import com.owncloud.android.utils.NOTIFICATION_TIMEOUT_STANDARD
import com.owncloud.android.utils.NotificationUtils.createBasicNotification
import com.owncloud.android.utils.RemoteFileUtils.getAvailableRemotePath
import com.owncloud.android.workers.unzip.UnzipWorkerPersistedState
import com.owncloud.android.workers.unzip.UnzipWorkerStateStore
import com.owncloud.android.workers.unzip.toDomain
import com.owncloud.android.workers.unzip.toPersisted
import kotlinx.coroutines.CancellationException
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
    private lateinit var stateStore: UnzipWorkerStateStore
    private var extractState: UnzipWorkerPersistedState? = null
    private var targetRemotePath: String? = null
    private var lastProgressPercent = 0
    private var uploadBytesTotal = 0L
    private var uploadedBytes = 0L
    private var filesToUploadCount = 0
    private var uploadedFilesCount = 0

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ArchiveWorkerForeground.createForegroundInfo(
            context = appContext,
            notificationId = ArchiveWorkerForeground.notificationIdFor(workerParameters.id),
            title = foregroundNotificationTitle(),
        )

    override suspend fun doWork(): Result {
        if (!areParametersValid()) return Result.failure()
        setForeground(getForegroundInfo())

        stateStore = UnzipWorkerStateStore(
            temporalFolderPath = FileStorageUtils.getTemporalPath(account.name, zipFile.spaceId),
            workerId = workerParameters.id,
        )
        extractState = stateStore.load()
        targetRemotePath = extractState?.baseRemotePath

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
            val extractedLayout = ZipArchiveExtractor.extract(
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
                isCancelled = { isStopped },
            )
            val layout = extractState?.layout?.toDomain() ?: extractedLayout
            progress.completePhase(UnzipPhase.EXTRACT)
            ensureNotCancelled()

            val client = getClient()
            val isUserLogged = AccountUtils.getCurrentOwnCloudAccount(appContext) != null
            val targetPath = resolveTargetPath(client, layout, isUserLogged)

            when (layout) {
                is ArchiveExtractLayout.DirectToParent -> {
                    if (layout.isTopLevelFolder) {
                        createRemoteFolder(client, targetPath)
                    }
                }

                ArchiveExtractLayout.IntoArchiveFolder -> {
                    createRemoteFolder(client, targetPath)
                }
            }
            progress.completePhase(UnzipPhase.CREATE_FOLDER)

            val localUploadRoot = resolveLocalUploadRoot(extractDirectory, layout)
            uploadBytesTotal = if (localUploadRoot.isDirectory) {
                sumFileSizesRecursively(localUploadRoot)
            } else {
                localUploadRoot.length()
            }
            filesToUploadCount = if (localUploadRoot.isDirectory) {
                countFilesRecursively(localUploadRoot)
            } else {
                1
            }
            uploadedBytes = 0L
            uploadedFilesCount = 0
            if (localUploadRoot.isDirectory) {
                uploadDirectoryRecursively(
                    client = client,
                    localDirectory = localUploadRoot,
                    remoteBasePath = targetPath,
                )
            } else {
                uploadFile(client, localUploadRoot, targetPath)
            }
            progress.completePhase(UnzipPhase.UPLOAD)
            ensureNotCancelled()

            fileRepository.refreshFolder(
                remotePath = zipFile.getParentRemotePath(),
                accountName = account.name,
                spaceId = zipFile.spaceId,
            )

            progress.reportComplete()
            stateStore.clear()
            extractState = null
            notifyUnzipResult(throwable = null, zipFileName = zipFileName)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Timber.e(throwable, "Unzip operation failed")
            notifyUnzipResult(throwable = throwable, zipFileName = zipFileName)
        } finally {
            cleanupTempFiles()
        }
    }

    private fun foregroundNotificationTitle(): String {
        val zipFileName = if (::zipFile.isInitialized) {
            zipFile.fileName
        } else {
            val zipFileId = workerParameters.inputData.getLong(KEY_PARAM_ZIP_FILE_ID, -1)
            getFileByIdUseCase(GetFileByIdUseCase.Params(zipFileId)).getDataOrNull()?.fileName.orEmpty()
        }
        return appContext.getString(R.string.homecloud_filelist_extract_enqueued, zipFileName)
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
                notificationId = ArchiveWorkerForeground.notificationIdFor(workerParameters.id),
                intent = null,
                contentText = contentText,
                timeOut = timeOut,
            )
        }

        return when {
            throwable == null -> {
                val targetPath = targetRemotePath
                if (targetPath != null) {
                    Result.success(workDataOf(KEY_TARGET_REMOTE_PATH to targetPath))
                } else {
                    Result.success()
                }
            }
            throwable is NoNetworkConnectionException -> Result.retry()
            else -> {
                val failureType = ArchiveFailureClassifier.classify(throwable)
                    ?: return Result.failure()
                Result.failure(workDataOf(KEY_ARCHIVE_FAILURE_TYPE to failureType.name))
            }
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

    private suspend fun resolveTargetPath(
        client: OwnCloudClient,
        layout: ArchiveExtractLayout,
        isUserLogged: Boolean,
    ): String {
        targetRemotePath?.let { return it }

        val path = when (layout) {
            is ArchiveExtractLayout.DirectToParent -> {
                val parentPath = normalizeRemoteFolderPath(zipFile.getParentRemotePath())
                val available = getAvailableRemotePath(
                    ownCloudClient = client,
                    remotePath = parentPath + layout.topLevelRoot,
                    spaceWebDavUrl = spaceWebDavUrl,
                    isUserLogged = isUserLogged,
                )
                if (layout.isTopLevelFolder) {
                    normalizeRemoteFolderPath(available)
                } else {
                    available
                }
            }

            ArchiveExtractLayout.IntoArchiveFolder -> {
                getAvailableRemotePath(
                    ownCloudClient = client,
                    remotePath = ArchiveNameResolver.resolveExtractSubfolderPath(zipFile)
                        .trimEnd(OCFile.PATH_SEPARATOR),
                    spaceWebDavUrl = spaceWebDavUrl,
                    isUserLogged = isUserLogged,
                ) + OCFile.PATH_SEPARATOR
            }
        }

        targetRemotePath = path
        persistState(
            UnzipWorkerPersistedState(
                layout = layout.toPersisted(),
                baseRemotePath = path,
            ),
        )
        setProgress(
            mergeTargetPathIntoProgress(
                workDataOf(DownloadFileWorker.WORKER_KEY_PROGRESS to lastProgressPercent),
            ),
        )
        return path
    }

    private fun resolveLocalUploadRoot(
        extractDirectory: File,
        layout: ArchiveExtractLayout,
    ): File =
        when (layout) {
            is ArchiveExtractLayout.DirectToParent -> File(extractDirectory, layout.topLevelRoot)
            ArchiveExtractLayout.IntoArchiveFolder -> extractDirectory
        }

    private fun persistState(state: UnzipWorkerPersistedState) {
        extractState = state
        stateStore.save(state)
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

    private suspend fun uploadDirectoryRecursively(
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
        ensureNotCancelled()
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
    }
}
