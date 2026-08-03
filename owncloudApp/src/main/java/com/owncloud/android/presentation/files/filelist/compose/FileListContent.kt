package com.owncloud.android.presentation.files.filelist.compose

/**
 * Sealed content for [FileList]: loading placeholder, empty dataset, or item rows (+ optional footer).
 */
sealed interface FileListContent {
    data object Loading : FileListContent

    data class Empty(
        val model: FileListEmptyUiModel,
    ) : FileListContent

    data class Items(
        val items: List<FileListItemUiModel>,
        val footerText: String? = null,
    ) : FileListContent
}
