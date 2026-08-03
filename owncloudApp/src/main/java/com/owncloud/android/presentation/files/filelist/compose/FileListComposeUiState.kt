package com.owncloud.android.presentation.files.filelist.compose

import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.model.isVirtualFile

/**
 * Fully rendered file-list state for Compose hosts (Main, Favorites, Search, Tags, …).
 * Fragments display this and forward user events to their ViewModel.
 */
data class FileListComposeUiState(
    val folderContent: List<OCFileWithSyncInfo> = emptyList(),
    val content: FileListContent = FileListContent.Loading,
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

    /** Ids that participate in select-all / inverse (excludes virtual upload rows). */
    fun selectableFileIds(): List<Long> =
        folderContent.mapNotNull { info ->
            info.file.id?.takeUnless { info.file.isVirtualFile() }
        }

    fun checkedItems(): List<OCFileWithSyncInfo> =
        folderContent.filter { it.file.id in selectedIds }

    fun findFile(fileId: Long): OCFileWithSyncInfo? =
        folderContent.find { it.file.id == fileId }
}
