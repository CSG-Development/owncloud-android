package com.owncloud.android.presentation.files.filelist.compose

import android.accounts.Account
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID

private val NoArchiveActivityFlow: StateFlow<ArchiveActivityUiModel?> = MutableStateFlow(null)

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
    modifier: Modifier = Modifier,
    scrollToTopEvents: Flow<Unit> = emptyFlow(),
    archiveActivityFlow: StateFlow<ArchiveActivityUiModel?> = NoArchiveActivityFlow,
    onArchiveActivityCancel: (UUID) -> Unit = {},
    onItemClick: (fileId: Long) -> Unit,
    onItemLongClick: (fileId: Long) -> Unit = {},
    onThreeDotClick: (fileId: Long) -> Unit = {},
    onVirtualOpenUploads: () -> Unit = {},
    onVirtualCancelUpload: (fileId: Long) -> Unit = {},
    onRefresh: (() -> Unit)? = null,
    onSelectionBecameEmpty: (() -> Unit)? = null,
) {
    val composeState by uiStateFlow.collectAsState()
    val archiveActivity by archiveActivityFlow.collectAsState()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val layoutModeState = rememberUpdatedState(composeState.layoutMode)
    val onSelectionBecameEmptyState = rememberUpdatedState(onSelectionBecameEmpty)

    LaunchedEffect(composeState.hasSelection) {
        if (!composeState.hasSelection) {
            onSelectionBecameEmptyState.value?.invoke()
        }
    }

    LaunchedEffect(scrollToTopEvents) {
        scrollToTopEvents.collect {
            when (layoutModeState.value) {
                FileListLayoutMode.List -> listState.scrollToItem(0)
                FileListLayoutMode.Grid -> gridState.scrollToItem(0)
            }
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
        val onVirtualOpenUploadsState = rememberUpdatedState(onVirtualOpenUploads)
        val onVirtualCancelUploadState = rememberUpdatedState(onVirtualCancelUpload)
        val onRefreshState = rememberUpdatedState(onRefresh)
        val onArchiveActivityCancelState = rememberUpdatedState(onArchiveActivityCancel)

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
            archiveActivity = archiveActivity,
            onArchiveActivityCancel = { workId -> onArchiveActivityCancelState.value(workId) },
            modifier = modifier.fillMaxSize(),
            thumbnail = thumbnail,
            onItemClick = { onItemClickState.value(it.fileId) },
            onItemLongClick = { onItemLongClickState.value(it.fileId) },
            onThreeDotClick = { onThreeDotClickState.value(it.fileId) },
            onVirtualOpenUploads = { onVirtualOpenUploadsState.value() },
            onVirtualCancelUpload = { fileId -> onVirtualCancelUploadState.value(fileId) },
        )
    }
}

/**
 * Binds [FileListHost] to a [ComposeView] with the standard composition strategy and touch filter.
 */
fun ComposeView.setFileListContent(
    uiStateFlow: StateFlow<FileListComposeUiState>,
    account: Account?,
    onItemClick: (fileId: Long) -> Unit,
    onItemLongClick: (fileId: Long) -> Unit = {},
    onThreeDotClick: (fileId: Long) -> Unit = {},
    onVirtualOpenUploads: () -> Unit = {},
    onVirtualCancelUpload: (fileId: Long) -> Unit = {},
    onRefresh: (() -> Unit)? = null,
    onSelectionBecameEmpty: (() -> Unit)? = null,
    scrollToTopEvents: Flow<Unit> = emptyFlow(),
    archiveActivityFlow: StateFlow<ArchiveActivityUiModel?> = NoArchiveActivityFlow,
    onArchiveActivityCancel: (UUID) -> Unit = {},
) {
    filterTouchesWhenObscured =
        PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        FileListHost(
            uiStateFlow = uiStateFlow,
            account = account,
            scrollToTopEvents = scrollToTopEvents,
            archiveActivityFlow = archiveActivityFlow,
            onArchiveActivityCancel = onArchiveActivityCancel,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onThreeDotClick = onThreeDotClick,
            onVirtualOpenUploads = onVirtualOpenUploads,
            onVirtualCancelUpload = onVirtualCancelUpload,
            onRefresh = onRefresh,
            onSelectionBecameEmpty = onSelectionBecameEmpty,
        )
    }
}
