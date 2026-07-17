package com.owncloud.android.presentation.files.filelist.compose

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.owncloud.android.R
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudTheme

enum class FileListLayoutMode {
    List,
    Grid,
}

/**
 * Lazy file-list container (list or grid) with optional footer and pull-to-refresh.
 * Hosts own selection and callbacks; fragments supply items and chrome.
 *
 * When [onRefresh] is non-null and [pullToRefreshEnabled] is true, content is wrapped in [PullToRefreshBox].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileList(
    items: List<FileListItemUiModel>,
    layoutMode: FileListLayoutMode,
    modifier: Modifier = Modifier,
    selectedIds: Set<Long> = emptySet(),
    footerText: String? = null,
    gridColumns: Int = 3,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    isRefreshing: Boolean = false,
    pullToRefreshEnabled: Boolean = true,
    onRefresh: (() -> Unit)? = null,
    emptyContent: FileListEmptyUiModel? = null,
    thumbnail: @Composable (FileListItemUiModel) -> Bitmap? = { null },
    onItemClick: (FileListItemUiModel) -> Unit = {},
    onItemLongClick: (FileListItemUiModel) -> Unit = {},
    onThreeDotClick: (FileListItemUiModel) -> Unit = {},
) {
    val listContent: @Composable (Modifier) -> Unit = { contentModifier ->
        if (items.isEmpty() && emptyContent != null) {
            // LazyColumn is required so PullToRefreshBox receives nested-scroll events when empty.
            LazyColumn(
                modifier = contentModifier,
                state = listState,
            ) {
                item(key = EMPTY_CONTENT_KEY) {
                    FileListEmpty(
                        content = emptyContent,
                        modifier = Modifier.fillParentMaxSize(),
                    )
                }
            }
        } else {
            when (layoutMode) {
                FileListLayoutMode.List -> FileListLazyColumn(
                    items = items,
                    selectedIds = selectedIds,
                    footerText = footerText,
                    listState = listState,
                    thumbnail = thumbnail,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    onThreeDotClick = onThreeDotClick,
                    modifier = contentModifier,
                )

                FileListLayoutMode.Grid -> FileListLazyGrid(
                    items = items,
                    selectedIds = selectedIds,
                    footerText = footerText,
                    gridColumns = gridColumns.coerceAtLeast(1),
                    gridState = gridState,
                    thumbnail = thumbnail,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    modifier = contentModifier,
                )
            }
        }
    }

    // Material3 1.3 PullToRefreshBox has no `enabled` flag yet — omit the box when disabled.
    if (onRefresh != null && pullToRefreshEnabled) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
        ) {
            // fillMaxSize so an empty list remains a valid pull target
            listContent(Modifier.fillMaxSize())
        }
    } else {
        listContent(modifier)
    }
}

@Composable
private fun FileListLazyColumn(
    items: List<FileListItemUiModel>,
    selectedIds: Set<Long>,
    footerText: String?,
    listState: LazyListState,
    thumbnail: @Composable (FileListItemUiModel) -> Bitmap?,
    onItemClick: (FileListItemUiModel) -> Unit,
    onItemLongClick: (FileListItemUiModel) -> Unit,
    onThreeDotClick: (FileListItemUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
    ) {
        items(
            items = items,
            key = { it.fileId },
        ) { item ->
            FileListLazyRow(
                item = item,
                selectedIds = selectedIds,
                thumbnail = thumbnail,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                onThreeDotClick = onThreeDotClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimensionResource(R.dimen.item_file_list_min_height)),
            )
        }
        if (footerText != null) {
            item(key = FOOTER_KEY) {
                FileListFooter(
                    text = footerText,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FileListLazyGrid(
    items: List<FileListItemUiModel>,
    selectedIds: Set<Long>,
    footerText: String?,
    gridColumns: Int,
    gridState: LazyGridState,
    thumbnail: @Composable (FileListItemUiModel) -> Bitmap?,
    onItemClick: (FileListItemUiModel) -> Unit,
    onItemLongClick: (FileListItemUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        modifier = modifier,
        state = gridState,
    ) {
        items(
            items = items,
            key = { it.fileId },
        ) { item ->
            FileListLazyGridCell(
                item = item,
                selectedIds = selectedIds,
                thumbnail = thumbnail,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (footerText != null) {
            item(
                key = FOOTER_KEY,
                span = { GridItemSpan(maxLineSpan) },
            ) {
                FileListFooter(
                    text = footerText,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FileListLazyRow(
    item: FileListItemUiModel,
    selectedIds: Set<Long>,
    thumbnail: @Composable (FileListItemUiModel) -> Bitmap?,
    onItemClick: (FileListItemUiModel) -> Unit,
    onItemLongClick: (FileListItemUiModel) -> Unit,
    onThreeDotClick: (FileListItemUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayItem = item.withSelection(selectedIds)
    FileListRow(
        item = displayItem,
        thumbnail = thumbnail(displayItem),
        modifier = modifier,
        onClick = { onItemClick(item) },
        onLongClick = { onItemLongClick(item) },
        onThreeDotClick = { onThreeDotClick(item) },
    )
}

@Composable
private fun FileListLazyGridCell(
    item: FileListItemUiModel,
    selectedIds: Set<Long>,
    thumbnail: @Composable (FileListItemUiModel) -> Bitmap?,
    onItemClick: (FileListItemUiModel) -> Unit,
    onItemLongClick: (FileListItemUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayItem = item.withSelection(selectedIds)
    val thumb = thumbnail(displayItem)
    FileGridItem(
        item = displayItem,
        thumbnail = thumb,
        expandedThumbnail = displayItem.isImage && thumb != null,
        modifier = modifier,
        onClick = { onItemClick(item) },
        onLongClick = { onItemLongClick(item) },
    )
}

/**
 * Overlays selection ids onto a row/cell model for rendering.
 * When selection is active, checkbox chrome replaces the three-dot menu (non-virtual only).
 */
private fun FileListItemUiModel.withSelection(
    selectedIds: Set<Long>,
): FileListItemUiModel {
    val selected = fileId in selectedIds
    return if (selectedIds.isNotEmpty()) {
        copy(
            isSelected = selected,
            showCheckbox = !isVirtual,
            showThreeDotMenu = false,
        )
    } else {
        copy(isSelected = false)
    }
}

private const val FOOTER_KEY = "file_list_footer"
private const val EMPTY_CONTENT_KEY = "file_list_empty"

private fun previewItems(count: Int): List<FileListItemUiModel> {
    val base = FileListItemUiModelFixtures.all
    return List(count) { index ->
        val source = base[index % base.size]
        source.copy(
            fileId = index.toLong() + 1L,
            name = "${source.name} ($index)",
        )
    }
}

@HomeCloudPreview
@Composable
private fun FileListShortListPreview() {
    HomeCloudTheme {
        Surface {
            FileList(
                items = previewItems(5),
                layoutMode = FileListLayoutMode.List,
                footerText = "3 files, 2 folders",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun FileListLongListPreview() {
    HomeCloudTheme {
        Surface {
            FileList(
                items = previewItems(40),
                layoutMode = FileListLayoutMode.List,
                footerText = "30 files, 10 folders",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(640.dp),
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun FileListGridPreview() {
    HomeCloudTheme {
        Surface {
            FileList(
                items = previewItems(12),
                layoutMode = FileListLayoutMode.Grid,
                gridColumns = 3,
                footerText = "8 files, 4 folders",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(640.dp),
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun FileListSelectionModePreview() {
    var selectedIds by remember { mutableStateOf(setOf(1L, 3L)) }
    HomeCloudTheme {
        Surface {
            FileList(
                items = previewItems(6),
                layoutMode = FileListLayoutMode.List,
                selectedIds = selectedIds,
                footerText = "4 files, 2 folders",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
                onItemClick = { item ->
                    selectedIds = if (item.fileId in selectedIds) {
                        selectedIds - item.fileId
                    } else {
                        selectedIds + item.fileId
                    }
                },
                onItemLongClick = { selectedIds = selectedIds + it.fileId },
            )
        }
    }
}
