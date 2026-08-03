package com.owncloud.android.presentation.files.operations

data class ArchiveWorkCompleted(
    val isCompress: Boolean,
    val itemCount: Int,
    val viewFolderId: Long,
)
