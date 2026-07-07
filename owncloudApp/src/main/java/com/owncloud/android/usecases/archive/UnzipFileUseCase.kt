package com.owncloud.android.usecases.archive

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.owncloud.android.domain.BaseUseCase
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.workers.UnzipFileWorker
import timber.log.Timber
import java.util.UUID

class UnzipFileUseCase(
    private val workManager: WorkManager,
) : BaseUseCase<UUID?, UnzipFileUseCase.Params>() {

    override fun run(params: Params): UUID? {
        val zipFileId = params.zipFile.id ?: return null

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

    data class Params(
        val accountName: String,
        val zipFile: OCFile,
    )
}
