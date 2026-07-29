package com.owncloud.android.presentation.files.operations

data class ArchiveWorkFailed(
    val failureType: ArchiveFailureType,
    val isCompress: Boolean,
    val displayName: String,
    val sourceFileIds: List<Long>,
    val zipFileId: Long?,
    val parentFolderId: Long,
    val spaceId: String?,
    val accountName: String,
)
