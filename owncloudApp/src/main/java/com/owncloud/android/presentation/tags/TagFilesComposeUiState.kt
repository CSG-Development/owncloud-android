package com.owncloud.android.presentation.tags

import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.presentation.files.filelist.compose.FileListContent
import com.owncloud.android.presentation.files.filelist.compose.FileListLayoutMode

/**
 * Fully rendered Tag Files list state for Compose.
 * The Fragment displays this and forwards user events to [TagFilesViewModel].
 */
data class TagFilesComposeUiState(
    val folderContent: List<OCFileWithSyncInfo> = emptyList(),
    val content: FileListContent = FileListContent.Loading,
    val layoutMode: FileListLayoutMode = FileListLayoutMode.List,
    val gridColumns: Int = 3,
    val isRefreshing: Boolean = false,
) {
    fun findFile(fileId: Long): OCFileWithSyncInfo? =
        folderContent.find { it.file.id == fileId }
}
