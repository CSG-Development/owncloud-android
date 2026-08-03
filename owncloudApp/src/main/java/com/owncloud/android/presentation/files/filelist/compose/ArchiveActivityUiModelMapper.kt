package com.owncloud.android.presentation.files.filelist.compose

import androidx.work.WorkInfo
import com.owncloud.android.presentation.files.operations.ArchiveWorkEnqueued
import com.owncloud.android.usecases.archive.ARCHIVE_TAG_UNZIP
import com.owncloud.android.usecases.archive.ARCHIVE_TAG_ZIP
import com.owncloud.android.workers.DownloadFileWorker
import java.util.UUID

object ArchiveActivityUiModelMapper {

    fun fromPendingWorks(
        accountName: String,
        pendingWorks: List<WorkInfo>,
        workMetadata: Map<UUID, ArchiveWorkEnqueued>,
    ): ArchiveActivityUiModel? {
        val operations = pendingWorks.mapNotNull { workInfo ->
            val metadata = workMetadata[workInfo.id] ?: return@mapNotNull null
            if (metadata.accountName != accountName) return@mapNotNull null
            val expectedTag = if (metadata.isCompress) ARCHIVE_TAG_ZIP else ARCHIVE_TAG_UNZIP
            if (!workInfo.tags.contains(expectedTag)) return@mapNotNull null

            val rawProgress = workInfo.progress.getInt(DownloadFileWorker.WORKER_KEY_PROGRESS, -1)
            ArchiveActivityOperationUiModel(
                workId = workInfo.id,
                displayName = metadata.displayName,
                isCompress = metadata.isCompress,
                progress = if (rawProgress < 0) null else rawProgress.coerceIn(0, 100),
            )
        }
        return if (operations.isEmpty()) null else ArchiveActivityUiModel(operations)
    }
}
