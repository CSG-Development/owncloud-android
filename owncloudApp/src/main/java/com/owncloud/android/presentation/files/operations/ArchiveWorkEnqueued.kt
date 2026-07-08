package com.owncloud.android.presentation.files.operations

import java.util.UUID

data class ArchiveWorkEnqueued(
    val workId: UUID,
    val displayName: String,
    val isCompress: Boolean,
)
