package com.owncloud.android.presentation.files.filelist.compose

import androidx.work.WorkInfo
import com.owncloud.android.usecases.archive.ARCHIVE_TAG_UNZIP
import com.owncloud.android.usecases.archive.ARCHIVE_TAG_ZIP
import com.owncloud.android.usecases.archive.ArchiveWorkTags
import com.owncloud.android.workers.DownloadFileWorker

object ArchiveActivityUiModelMapper {

    fun fromPendingWorks(
        accountName: String,
        pendingWorks: List<WorkInfo>,
    ): ArchiveActivityUiModel? {
        val operations = pendingWorks.mapNotNull { workInfo ->
            if (!workInfo.tags.contains(accountName)) return@mapNotNull null

            val isCompress = when {
                workInfo.tags.contains(ARCHIVE_TAG_ZIP) -> true
                workInfo.tags.contains(ARCHIVE_TAG_UNZIP) -> false
                else -> return@mapNotNull null
            }

            val displayName = ArchiveWorkTags.parseDisplayName(workInfo.tags) ?: return@mapNotNull null
            val rawProgress = workInfo.progress.getInt(DownloadFileWorker.WORKER_KEY_PROGRESS, -1)
            ArchiveActivityOperationUiModel(
                workId = workInfo.id,
                displayName = displayName,
                isCompress = isCompress,
                progress = if (rawProgress < 0) null else rawProgress.coerceIn(0, 100),
            )
        }
        return if (operations.isEmpty()) null else ArchiveActivityUiModel(operations)
    }
}
