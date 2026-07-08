package com.owncloud.android.usecases.archive

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.owncloud.android.domain.BaseUseCase
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.extensions.PENDING_WORK_STATUS
import com.owncloud.android.extensions.buildWorkQuery
import com.owncloud.android.usecases.transfers.MAXIMUM_NUMBER_OF_RETRIES
import com.owncloud.android.workers.UnzipFileWorker
import timber.log.Timber
import java.util.UUID

class UnzipFileUseCase(
    private val workManager: WorkManager,
) : BaseUseCase<UUID?, UnzipFileUseCase.Params>() {

    override fun run(params: Params): UUID? {
        val zipFileId = params.zipFile.id ?: return null

        if (isUnzipAlreadyEnqueued(params.accountName, zipFileId)) {
            return null
        }

        val inputData = workDataOf(
            UnzipFileWorker.KEY_PARAM_ACCOUNT to params.accountName,
            UnzipFileWorker.KEY_PARAM_ZIP_FILE_ID to zipFileId,
        )

        val unzipWork = OneTimeWorkRequestBuilder<UnzipFileWorker>()
            .setInputData(inputData)
            .addTag(params.accountName)
            .addTag(ARCHIVE_TAG_UNZIP)
            .addTag(zipFileId.toString())
            .build()

        workManager.enqueue(unzipWork)
        Timber.i("Unzip operation enqueued for ${params.zipFile.fileName}.")
        return unzipWork.id
    }

    private fun isUnzipAlreadyEnqueued(accountName: String, zipFileId: Long): Boolean {
        val tagsToFilter = listOf(ARCHIVE_TAG_UNZIP, accountName, zipFileId.toString())
        val workQuery = buildWorkQuery(
            tags = tagsToFilter,
            states = PENDING_WORK_STATUS,
        )

        val unzipWorkers = workManager.getWorkInfos(workQuery).get()
            .filter { it.tags.containsAll(tagsToFilter) }

        var isEnqueued = false
        unzipWorkers.forEach { workInfo ->
            if (workInfo.runAttemptCount > MAXIMUM_NUMBER_OF_RETRIES) {
                workManager.cancelWorkById(workInfo.id)
            } else {
                isEnqueued = true
            }
        }

        if (isEnqueued) {
            Timber.i("Unzip operation for file id $zipFileId has not finished yet. Do not enqueue it again.")
        }

        return isEnqueued
    }

    data class Params(
        val accountName: String,
        val zipFile: OCFile,
    )
}
