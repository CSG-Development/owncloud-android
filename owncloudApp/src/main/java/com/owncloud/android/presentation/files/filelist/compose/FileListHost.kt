package com.owncloud.android.presentation.files.filelist.compose

import android.accounts.Account
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.owncloud.android.domain.files.model.isVirtualFile
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import com.owncloud.android.utils.PreferenceUtils
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared Compose host for file-list screens: theme, state collection, thumbnails, callbacks.
 *
 * [onSelectionBecameEmpty] is invoked when selection goes from non-empty to empty
 * (e.g. finish ActionMode after folder change prunes ids).
 */
@Composable
fun FileListHost(
    uiStateFlow: StateFlow<FileListComposeUiState>,
    account: Account?,
    listState: LazyListState,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    onItemClick: (fileId: Long) -> Unit,
    onItemLongClick: (fileId: Long) -> Unit = {},
    onThreeDotClick: (fileId: Long) -> Unit = {},
    onRefresh: (() -> Unit)? = null,
    onSelectionBecameEmpty: (() -> Unit)? = null,
) {
    val composeState by uiStateFlow.collectAsState()
    val onSelectionBecameEmptyState = rememberUpdatedState(onSelectionBecameEmpty)

    LaunchedEffect(composeState.hasSelection) {
        if (!composeState.hasSelection) {
            onSelectionBecameEmptyState.value?.invoke()
        }
    }

    HomeCloudTheme {
        val filesById = remember(composeState.folderContent) {
            composeState.folderContent.associateBy { it.file.id }
        }
        val filesByIdState = rememberUpdatedState(filesById)
        val accountState = rememberUpdatedState(account)
        val onItemClickState = rememberUpdatedState(onItemClick)
        val onItemLongClickState = rememberUpdatedState(onItemLongClick)
        val onThreeDotClickState = rememberUpdatedState(onThreeDotClick)
        val onRefreshState = rememberUpdatedState(onRefresh)

        val thumbnail: @Composable (FileListItemUiModel) -> Bitmap? = remember {
            { item ->
                val file = filesByIdState.value[item.fileId]?.file
                rememberFileListThumbnail(
                    file = file?.takeUnless { it.isFolder || it.isVirtualFile() },
                    account = accountState.value,
                )
            }
        }

        FileList(
            content = composeState.content,
            layoutMode = composeState.layoutMode,
            selectedIds = composeState.selectedIds,
            gridColumns = composeState.gridColumns,
            listState = listState,
            gridState = gridState,
            isRefreshing = composeState.isRefreshing,
            pullToRefreshEnabled = composeState.pullToRefreshEnabled,
            onRefresh = onRefreshState.value?.let { refresh -> { refresh() } },
            modifier = modifier.fillMaxSize(),
            thumbnail = thumbnail,
            onItemClick = { onItemClickState.value(it.fileId) },
            onItemLongClick = { onItemLongClickState.value(it.fileId) },
            onThreeDotClick = { onThreeDotClickState.value(it.fileId) },
        )
    }
}

/**
 * Binds [FileListHost] to a [ComposeView] with the standard composition strategy and touch filter.
 */
fun ComposeView.setFileListContent(
    uiStateFlow: StateFlow<FileListComposeUiState>,
    account: Account?,
    listState: LazyListState,
    gridState: LazyGridState,
    onItemClick: (fileId: Long) -> Unit,
    onItemLongClick: (fileId: Long) -> Unit = {},
    onThreeDotClick: (fileId: Long) -> Unit = {},
    onRefresh: (() -> Unit)? = null,
    onSelectionBecameEmpty: (() -> Unit)? = null,
) {
    filterTouchesWhenObscured =
        PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        FileListHost(
            uiStateFlow = uiStateFlow,
            account = account,
            listState = listState,
            gridState = gridState,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onThreeDotClick = onThreeDotClick,
            onRefresh = onRefresh,
            onSelectionBecameEmpty = onSelectionBecameEmpty,
        )
    }
}
