package com.owncloud.android.presentation.files.filelist.compose

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class ArchiveActivityUiModel(
    val operations: List<ArchiveActivityOperationUiModel>,
) {
    val operationCount: Int
        get() = operations.size

    val overallProgress: Int?
        get() {
            val determinate = operations.mapNotNull { it.progress }
            if (determinate.isEmpty()) return null
            return determinate.average().toInt().coerceIn(0, 100)
        }

    val isOverallProgressIndeterminate: Boolean
        get() = overallProgress == null
}

@Immutable
data class ArchiveActivityOperationUiModel(
    val workId: UUID,
    val displayName: String,
    val isCompress: Boolean,
    val progress: Int?,
) {
    val isProgressIndeterminate: Boolean
        get() = progress == null
}
