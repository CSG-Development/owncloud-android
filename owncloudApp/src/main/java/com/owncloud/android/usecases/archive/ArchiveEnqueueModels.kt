package com.owncloud.android.usecases.archive

import java.util.UUID

data class ArchiveEnqueueResult(
    val workId: UUID,
    val displayName: String,
    val isCompress: Boolean,
)

sealed class ZipEnqueueOutcome {
    data class Success(val result: ArchiveEnqueueResult) : ZipEnqueueOutcome()
    data object SkippedAlreadyEnqueued : ZipEnqueueOutcome()
    data object InvalidParams : ZipEnqueueOutcome()
    data object NameResolutionFailed : ZipEnqueueOutcome()
}
