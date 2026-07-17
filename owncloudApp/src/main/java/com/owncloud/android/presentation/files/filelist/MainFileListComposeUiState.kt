package com.owncloud.android.presentation.files.filelist

import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.presentation.files.filelist.compose.FileListEmptyUiModel
import com.owncloud.android.presentation.files.filelist.compose.FileListItemUiModel
import com.owncloud.android.presentation.files.filelist.compose.FileListLayoutMode

/**
 * Fully rendered Main file-list state for Compose.
 * The Fragment / composables display this and forward user events to the ViewModel.
 */
data class MainFileListComposeUiState(
    val folderContent: List<OCFileWithSyncInfo> = emptyList(),
    val items: List<FileListItemUiModel> = emptyList(),
    val footerText: String? = null,
    val emptyContent: FileListEmptyUiModel? = null,
    val layoutMode: FileListLayoutMode = FileListLayoutMode.List,
    val gridColumns: Int = 3,
    val selectedIds: Set<Long> = emptySet(),
    val isRefreshing: Boolean = false,
    val pullToRefreshEnabled: Boolean = true,
) {
    val selectedItemCount: Int
        get() = selectedIds.size

    val hasSelection: Boolean
        get() = selectedIds.isNotEmpty()

    fun selectableFileIds(): List<Long> =
        folderContent.mapNotNull { it.file.id }

    fun checkedItems(): List<OCFileWithSyncInfo> =
        folderContent.filter { it.file.id in selectedIds }

    fun findFile(fileId: Long): OCFileWithSyncInfo? =
        folderContent.find { it.file.id == fileId }
}
