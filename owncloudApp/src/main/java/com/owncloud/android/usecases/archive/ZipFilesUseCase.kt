package com.owncloud.android.usecases.archive

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.owncloud.android.domain.BaseUseCase
import com.owncloud.android.domain.archive.ArchiveNameResolver
import com.owncloud.android.domain.files.FileRepository
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.extensions.PENDING_WORK_STATUS
import com.owncloud.android.extensions.buildWorkQuery
import com.owncloud.android.usecases.transfers.MAXIMUM_NUMBER_OF_RETRIES
import com.owncloud.android.workers.ZipFilesWorker
import timber.log.Timber

class ZipFilesUseCase(
    private val workManager: WorkManager,
    private val fileRepository: FileRepository,
) : BaseUseCase<ZipEnqueueOutcome, ZipFilesUseCase.Params>() {

    override fun run(params: Params): ZipEnqueueOutcome {
        val fileIds = params.files.mapNotNull { it.id }
        val parentFolderId = params.parentFolder.id
            ?: return ZipEnqueueOutcome.InvalidParams
        if (fileIds.isEmpty()) {
            return ZipEnqueueOutcome.InvalidParams
        }

        if (isZipAlreadyEnqueued(params.accountName, fileIds)) {
            return ZipEnqueueOutcome.SkippedAlreadyEnqueued
        }

        // Serialize resolve + enqueue so concurrent compress calls cannot reserve the same path.
        synchronized(archiveNameReservationLock) {
            if (isZipAlreadyEnqueued(params.accountName, fileIds)) {
                return ZipEnqueueOutcome.SkippedAlreadyEnqueued
            }

            val pendingReservedRemotePaths = collectPendingReservedArchiveRemotePaths(
                accountName = params.accountName,
                parentFolderId = parentFolderId,
            )

            val baseFileName = ArchiveNameResolver.resolveArchiveBaseName(params.files)
            val baseRemotePath = ArchiveNameResolver.resolveRemoteZipPath(
                parentFolder = params.parentFolder,
                archiveFileName = baseFileName,
            )

            val remotePath = runCatching {
                fileRepository.getAvailableRemotePath(
                    remotePath = baseRemotePath,
                    accountName = params.accountName,
                    spaceId = params.parentFolder.spaceId,
                    isUserLogged = params.isUserLogged,
                    excludedRemotePaths = pendingReservedRemotePaths,
                )
            }.getOrElse { throwable ->
                Timber.e(throwable, "Failed to resolve available archive remote path")
                return ZipEnqueueOutcome.NameResolutionFailed
            }
            val archiveFileName = remotePath.substringAfterLast('/')

            val inputData = workDataOf(
                ZipFilesWorker.KEY_PARAM_ACCOUNT to params.accountName,
                ZipFilesWorker.KEY_PARAM_PARENT_FOLDER_ID to parentFolderId,
                ZipFilesWorker.KEY_PARAM_FILE_IDS to fileIds.toLongArray(),
                ZipFilesWorker.KEY_PARAM_ARCHIVE_FILE_NAME to archiveFileName,
            )

            val zipWork = OneTimeWorkRequestBuilder<ZipFilesWorker>()
                .setInputData(inputData)
                .addTag(params.accountName)
                .addTag(ARCHIVE_TAG_ZIP)
                .addTag(ArchiveWorkTags.displayNameTag(archiveFileName))
                .addTag(ArchiveWorkTags.parentTag(parentFolderId))
                .addTag(ArchiveWorkTags.remotePathTag(remotePath))
                .addTag(ArchiveWorkTags.itemCountTag(fileIds.size))
                .addTag(parentFolderId.toString())
                .apply {
                    fileIds.forEach { addTag(it.toString()) }
                }
                .build()

            workManager.enqueue(zipWork)
            Timber.i("Zip operation enqueued for ${params.files.size} item(s) as $archiveFileName.")
            return ZipEnqueueOutcome.Success(
                ArchiveEnqueueResult(
                    workId = zipWork.id,
                    displayName = archiveFileName,
                    isCompress = true,
                ),
            )
        }
    }

    private fun collectPendingReservedArchiveRemotePaths(
        accountName: String,
        parentFolderId: Long,
    ): Set<String> {
        val parentTag = parentFolderId.toString()
        return workManager.getWorkInfos(
            buildWorkQuery(
                tags = listOf(ARCHIVE_TAG_ZIP, accountName),
                states = PENDING_WORK_STATUS,
            ),
        ).get()
            .asSequence()
            .filter { it.tags.contains(ARCHIVE_TAG_ZIP) && it.tags.contains(accountName) }
            .filter { it.tags.contains(parentTag) || it.tags.contains(ArchiveWorkTags.parentTag(parentFolderId)) }
            .mapNotNull { workInfo -> ArchiveWorkTags.parseRemotePath(workInfo.tags) }
            .toSet()
    }

    private fun isZipAlreadyEnqueued(accountName: String, fileIds: List<Long>): Boolean {
        val tagsToFilter = listOf(ARCHIVE_TAG_ZIP, accountName)
        val workQuery = buildWorkQuery(
            tags = tagsToFilter,
            states = PENDING_WORK_STATUS,
        )

        val zipWorkers = workManager.getWorkInfos(workQuery).get()
            .filter { it.tags.containsAll(tagsToFilter) }

        var isEnqueued = false
        zipWorkers.forEach { workInfo ->
            if (workInfo.runAttemptCount > MAXIMUM_NUMBER_OF_RETRIES) {
                workManager.cancelWorkById(workInfo.id)
            } else if (fileIds.any { workInfo.tags.contains(it.toString()) }) {
                isEnqueued = true
            }
        }

        if (isEnqueued) {
            Timber.i("Zip operation for selected file(s) has not finished yet. Do not enqueue it again.")
        }

        return isEnqueued
    }

    data class Params(
        val accountName: String,
        val parentFolder: OCFile,
        val files: List<OCFile>,
        val isUserLogged: Boolean,
    )

    companion object {
        private val archiveNameReservationLock = Any()
    }
}
