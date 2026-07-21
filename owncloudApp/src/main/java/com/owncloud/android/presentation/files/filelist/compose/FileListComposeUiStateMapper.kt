package com.owncloud.android.presentation.files.filelist.compose

import android.content.Context
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.presentation.files.filelist.FileListFooterText

/**
 * Builds [FileListComposeUiState] for Loading / Empty / Items from shared mapping rules.
 */
fun fileListLoadingUiState(
    layoutMode: FileListLayoutMode,
    gridColumns: Int = 3,
    selectedIds: Set<Long> = emptySet(),
    isRefreshing: Boolean = false,
    pullToRefreshEnabled: Boolean = true,
): FileListComposeUiState = FileListComposeUiState(
    content = FileListContent.Loading,
    layoutMode = layoutMode,
    gridColumns = gridColumns,
    selectedIds = selectedIds,
    isRefreshing = isRefreshing,
    pullToRefreshEnabled = pullToRefreshEnabled,
)

fun fileListEmptyUiState(
    emptyModel: FileListEmptyUiModel,
    layoutMode: FileListLayoutMode,
    gridColumns: Int = 3,
    selectedIds: Set<Long> = emptySet(),
    isRefreshing: Boolean = false,
    pullToRefreshEnabled: Boolean = true,
): FileListComposeUiState = FileListComposeUiState(
    content = FileListContent.Empty(emptyModel),
    layoutMode = layoutMode,
    gridColumns = gridColumns,
    selectedIds = selectedIds,
    isRefreshing = isRefreshing,
    pullToRefreshEnabled = pullToRefreshEnabled,
)

/**
 * Maps [folderContent] to Items (or Empty when the list is empty).
 *
 * @param showSpacePathForItem when set, overrides [showSpacePath] per row (e.g. Main option rules).
 * @param footerContext when non-null and [includeFooter] is true, footer text is computed.
 */
fun fileListItemsUiState(
    folderContent: List<OCFileWithSyncInfo>,
    emptyModel: FileListEmptyUiModel,
    layoutMode: FileListLayoutMode,
    gridColumns: Int,
    selectedIds: Set<Long> = emptySet(),
    isRefreshing: Boolean = false,
    pullToRefreshEnabled: Boolean = true,
    showThreeDotMenu: Boolean = true,
    showSpacePath: Boolean = false,
    isMultiPersonal: Boolean = false,
    showSpacePathForItem: ((OCFileWithSyncInfo) -> Boolean)? = null,
    footerContext: Context? = null,
    includeFooter: Boolean = true,
): FileListComposeUiState {
    if (folderContent.isEmpty()) {
        return fileListEmptyUiState(
            emptyModel = emptyModel,
            layoutMode = layoutMode,
            gridColumns = gridColumns,
            selectedIds = selectedIds,
            isRefreshing = isRefreshing,
            pullToRefreshEnabled = pullToRefreshEnabled,
        )
    }

    val items = folderContent.map { info ->
        info.toFileListItemUiModel(
            showThreeDotMenu = showThreeDotMenu,
            showSpacePath = showSpacePathForItem?.invoke(info) ?: showSpacePath,
            isMultiPersonal = isMultiPersonal,
        )
    }
    val footerText = if (includeFooter && footerContext != null) {
        FileListFooterText.fromFiles(footerContext, folderContent)
    } else {
        null
    }

    return FileListComposeUiState(
        folderContent = folderContent,
        content = FileListContent.Items(
            items = items,
            footerText = footerText,
        ),
        layoutMode = layoutMode,
        gridColumns = gridColumns,
        selectedIds = selectedIds,
        isRefreshing = isRefreshing,
        pullToRefreshEnabled = pullToRefreshEnabled,
    )
}
