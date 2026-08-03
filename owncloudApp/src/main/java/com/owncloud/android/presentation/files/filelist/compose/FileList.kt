package com.owncloud.android.presentation.files.filelist.compose

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import java.util.UUID

enum class FileListLayoutMode {
    List,
    Grid,
}

/**
 * Lazy file-list container (list or grid) with optional footer and pull-to-refresh.
 * Hosts own selection and callbacks; fragments supply [FileListContent] and chrome.
 *
 * When [onRefresh] is non-null and [pullToRefreshEnabled] is true, content is wrapped in [PullToRefreshBox].
 * Optional [archiveActivity] is rendered as the first full-width scrollable item (list and grid).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileList(
    content: FileListContent,
    layoutMode: FileListLayoutMode,
    modifier: Modifier = Modifier,
    selectedIds: Set<Long> = emptySet(),
    gridColumns: Int = 3,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    isRefreshing: Boolean = false,
    pullToRefreshEnabled: Boolean = true,
    onRefresh: (() -> Unit)? = null,
    archiveActivity: ArchiveActivityUiModel? = null,
    onArchiveActivityCancel: (UUID) -> Unit = {},
    thumbnail: @Composable (FileListItemUiModel) -> Bitmap? = { null },
    onItemClick: (FileListItemUiModel) -> Unit = {},
    onItemLongClick: (FileListItemUiModel) -> Unit = {},
    onThreeDotClick: (FileListItemUiModel) -> Unit = {},
) {
    val listContent: @Composable (Modifier) -> Unit = { contentModifier ->
        when (content) {
            FileListContent.Loading -> {
                // Empty LazyColumn keeps PullToRefreshBox nested-scroll working while loading.
                LazyColumn(
                    modifier = contentModifier,
                    state = listState,
                ) {}
            }

            is FileListContent.Empty -> {
                LazyColumn(
                    modifier = contentModifier,
                    state = listState,
                ) {
                    item(key = EMPTY_CONTENT_KEY) {
                        FileListEmpty(
                            content = content.model,
                            modifier = Modifier.fillParentMaxSize(),
                        )
                    }
                }
            }

            is FileListContent.Items -> {
                when (layoutMode) {
                    FileListLayoutMode.List -> FileListLazyColumn(
                        items = content.items,
                        selectedIds = selectedIds,
                        footerText = content.footerText,
                        listState = listState,
                        archiveActivity = archiveActivity,
                        onArchiveActivityCancel = onArchiveActivityCancel,
                        thumbnail = thumbnail,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                        onThreeDotClick = onThreeDotClick,
                        modifier = contentModifier,
                    )

                    FileListLayoutMode.Grid -> FileListLazyGrid(
                        items = content.items,
                        selectedIds = selectedIds,
                        footerText = content.footerText,
                        gridColumns = gridColumns.coerceAtLeast(1),
                        gridState = gridState,
                        archiveActivity = archiveActivity,
                        onArchiveActivityCancel = onArchiveActivityCancel,
                        thumbnail = thumbnail,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                        modifier = contentModifier,
                    )
                }
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
    archiveActivity: ArchiveActivityUiModel?,
    onArchiveActivityCancel: (UUID) -> Unit,
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
        if (archiveActivity != null) {
            item(
                key = ARCHIVE_ACTIVITY_KEY,
                contentType = CONTENT_TYPE_ARCHIVE_ACTIVITY,
            ) {
                ArchiveActivityCard(
                    activity = archiveActivity,
                    onCancel = onArchiveActivityCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(R.dimen.standard_half_margin)),
                )
            }
        }
        items(
            items = items,
            key = { it.fileId },
            contentType = { it.lazyContentType() },
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
    archiveActivity: ArchiveActivityUiModel?,
    onArchiveActivityCancel: (UUID) -> Unit,
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
        if (archiveActivity != null) {
            item(
                key = ARCHIVE_ACTIVITY_KEY,
                contentType = CONTENT_TYPE_ARCHIVE_ACTIVITY,
                span = { GridItemSpan(maxLineSpan) },
            ) {
                ArchiveActivityCard(
                    activity = archiveActivity,
                    onCancel = onArchiveActivityCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(R.dimen.standard_half_margin)),
                )
            }
        }
        items(
            items = items,
            key = { it.fileId },
            contentType = { it.lazyContentType() },
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
 * Returns the same instance when selection is inactive to avoid per-row allocations while scrolling.
 */
private fun FileListItemUiModel.withSelection(
    selectedIds: Set<Long>,
): FileListItemUiModel {
    if (selectedIds.isEmpty()) {
        return this
    }
    return copy(
        isSelected = fileId in selectedIds,
        showCheckbox = !isVirtual,
        showThreeDotMenu = false,
    )
}

private fun FileListItemUiModel.lazyContentType(): String = when {
    isVirtual -> CONTENT_TYPE_VIRTUAL
    isFolder -> CONTENT_TYPE_FOLDER
    isImage -> CONTENT_TYPE_IMAGE
    else -> CONTENT_TYPE_FILE
}

private const val FOOTER_KEY = "file_list_footer"
private const val EMPTY_CONTENT_KEY = "file_list_empty"
private const val ARCHIVE_ACTIVITY_KEY = "archive_activity"
private const val CONTENT_TYPE_VIRTUAL = "virtual"
private const val CONTENT_TYPE_FOLDER = "folder"
private const val CONTENT_TYPE_IMAGE = "image"
private const val CONTENT_TYPE_FILE = "file"
private const val CONTENT_TYPE_ARCHIVE_ACTIVITY = "archive_activity"

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
                content = FileListContent.Items(
                    items = previewItems(5),
                    footerText = "3 files, 2 folders",
                ),
                layoutMode = FileListLayoutMode.List,
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
                content = FileListContent.Items(
                    items = previewItems(40),
                    footerText = "30 files, 10 folders",
                ),
                layoutMode = FileListLayoutMode.List,
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
                content = FileListContent.Items(
                    items = previewItems(12),
                    footerText = "8 files, 4 folders",
                ),
                layoutMode = FileListLayoutMode.Grid,
                gridColumns = 3,
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
                content = FileListContent.Items(
                    items = previewItems(6),
                    footerText = "4 files, 2 folders",
                ),
                layoutMode = FileListLayoutMode.List,
                selectedIds = selectedIds,
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

@HomeCloudPreview
@Composable
private fun FileListEmptyContentPreview() {
    HomeCloudTheme {
        Surface {
            FileList(
                content = FileListContent.Empty(
                    model = FileListEmptyUiModel(
                        iconRes = R.drawable.ic_search_2,
                        titleRes = R.string.homecloud_global_search_empty_title,
                        subtitleRes = R.string.homecloud_global_search_empty_subtitle,
                    ),
                ),
                layoutMode = FileListLayoutMode.List,
                archiveActivity = ArchiveActivityUiModelFixtures.threeOperationsMixed,
                pullToRefreshEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
            )
        }
    }
}
