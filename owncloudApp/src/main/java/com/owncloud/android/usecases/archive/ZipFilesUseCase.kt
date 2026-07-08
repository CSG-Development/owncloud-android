package com.owncloud.android.usecases.archive

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.owncloud.android.domain.BaseUseCase
import com.owncloud.android.domain.archive.ArchiveNameResolver
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.extensions.PENDING_WORK_STATUS
import com.owncloud.android.extensions.buildWorkQuery
import com.owncloud.android.usecases.transfers.MAXIMUM_NUMBER_OF_RETRIES
import com.owncloud.android.workers.ZipFilesWorker
import timber.log.Timber
import java.util.UUID

class ZipFilesUseCase(
    private val workManager: WorkManager,
) : BaseUseCase<UUID?, ZipFilesUseCase.Params>() {

    override fun run(params: Params): UUID? {
        val fileIds = params.files.mapNotNull { it.id }
        if (fileIds.isEmpty() || params.parentFolder.id == null) {
            return null
        }

        if (isZipAlreadyEnqueued(params.accountName, fileIds)) {
            return null
        }

        val displayName = ArchiveNameResolver.resolveArchiveBaseName(
            selectedFiles = params.files,
            parentFolder = params.parentFolder,
        )

        val inputData = workDataOf(
            ZipFilesWorker.KEY_PARAM_ACCOUNT to params.accountName,
            ZipFilesWorker.KEY_PARAM_PARENT_FOLDER_ID to params.parentFolder.id,
            ZipFilesWorker.KEY_PARAM_FILE_IDS to fileIds.toLongArray(),
            ZipFilesWorker.KEY_PARAM_DISPLAY_NAME to displayName,
            ZipFilesWorker.KEY_PARAM_SPACE_ID to params.parentFolder.spaceId,
        )

        val zipWork = OneTimeWorkRequestBuilder<ZipFilesWorker>()
            .setInputData(inputData)
            .addTag(params.accountName)
            .addTag(ARCHIVE_TAG_ZIP)
            .apply {
                fileIds.forEach { addTag(it.toString()) }
                params.parentFolder.id?.let { addTag(it.toString()) }
            }
            .build()

        workManager.enqueue(zipWork)
        Timber.i("Zip operation enqueued for ${params.files.size} item(s).")
        return zipWork.id
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
    )
}
