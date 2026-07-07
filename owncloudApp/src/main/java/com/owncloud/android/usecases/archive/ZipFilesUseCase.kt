package com.owncloud.android.usecases.archive

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.owncloud.android.domain.BaseUseCase
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.workers.ZipFilesWorker
import timber.log.Timber
import java.util.UUID

class ZipFilesUseCase(
    private val workManager: WorkManager,
) : BaseUseCase<UUID?, ZipFilesUseCase.Params>() {

    override fun run(params: Params): UUID? {
        val fileIds = params.files.mapNotNull { it.id }.toLongArray()
        if (fileIds.isEmpty() || params.parentFolder.id == null) {
            return null
        }

        val inputData = workDataOf(
            ZipFilesWorker.KEY_PARAM_ACCOUNT to params.accountName,
            ZipFilesWorker.KEY_PARAM_PARENT_FOLDER_ID to params.parentFolder.id,
            ZipFilesWorker.KEY_PARAM_FILE_IDS to fileIds,
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

    data class Params(
        val accountName: String,
        val parentFolder: OCFile,
        val files: List<OCFile>,
    )
}
