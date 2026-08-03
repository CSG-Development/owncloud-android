package com.owncloud.android.presentation.files.operations

import java.util.UUID

data class ArchiveWorkEnqueued(
    val workId: UUID,
    val displayName: String,
    val isCompress: Boolean,
    val itemCount: Int,
    val parentFolderId: Long,
    val remotePath: String,
    val spaceId: String?,
    val accountName: String,
    val sourceFileIds: List<Long> = emptyList(),
    val zipFileId: Long? = null,
)
