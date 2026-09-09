package com.owncloud.android.presentation.files.filelist.compose

import android.accounts.Account
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Velocity
import com.owncloud.android.domain.files.model.isVirtualFile
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import com.owncloud.android.ui.LandscapeBarsScrollSink
import com.owncloud.android.utils.PreferenceUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID
import kotlin.math.roundToInt

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
    scrollSink: LandscapeBarsScrollSink? = null,
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
        val scrollSinkState = rememberUpdatedState(scrollSink)
        val nestedScrollConnection = rememberLandscapeBarsNestedScrollConnection(scrollSinkState)

        LaunchedEffect(scrollSink) {
            if (scrollSink == null) return@LaunchedEffect
            snapshotFlow {
                val scrolling = listState.isScrollInProgress || gridState.isScrollInProgress
                val atTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0 &&
                    gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset == 0
                scrolling to atTop
            }.distinctUntilChanged().collect { (scrolling, atTop) ->
                if (!scrolling) {
                    scrollSinkState.value?.onLandscapeContentScrollIdle(atTop)
                }
            }
        }

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
            modifier = modifier
                .fillMaxSize()
                .then(if (scrollSink != null) Modifier.nestedScroll(nestedScrollConnection) else Modifier),
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
    scrollSink: LandscapeBarsScrollSink? = null,
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
            scrollSink = scrollSink,
        )
    }
}

@Composable
private fun rememberLandscapeBarsNestedScrollConnection(
    scrollSinkState: State<LandscapeBarsScrollSink?>,
): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val dy = -consumed.y.roundToInt()
                if (dy != 0) {
                    scrollSinkState.value?.onLandscapeContentScroll(
                        dy = dy,
                        isUserDragging = source == NestedScrollSource.UserInput,
                    )
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                scrollSinkState.value?.onLandscapeContentFling()
                return Velocity.Zero
            }
        }
    }
}
