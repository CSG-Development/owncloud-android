package com.owncloud.android.presentation.files.operations

import java.util.UUID

sealed class ArchiveEnqueueUiEvent {
    data class Enqueued(
        val workId: UUID,
        val displayName: String,
        val isCompress: Boolean,
    ) : ArchiveEnqueueUiEvent()

    data object EnqueueFailed : ArchiveEnqueueUiEvent()
}
