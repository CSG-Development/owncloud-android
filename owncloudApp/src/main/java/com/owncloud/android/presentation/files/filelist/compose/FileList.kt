package com.owncloud.android.presentation.files.filelist.compose

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
 * Lazy file-list container (list or grid) with optional footer.
 * Not wired to fragments yet — hosts own data, selection, and callbacks.
 */
@Composable
fun FileList(
    items: List<FileListItemUiModel>,
    layoutMode: FileListLayoutMode,
    modifier: Modifier = Modifier,
    selectionState: FileListSelectionState = rememberFileListSelectionState(),
    footerText: String? = null,
    gridColumns: Int = 3,
    thumbnail: @Composable (FileListItemUiModel) -> Bitmap? = { null },
    onItemClick: (FileListItemUiModel) -> Unit = {},
    onItemLongClick: (FileListItemUiModel) -> Unit = {},
    onThreeDotClick: (FileListItemUiModel) -> Unit = {},
) {
    when (layoutMode) {
        FileListLayoutMode.List -> FileListLazyColumn(
            items = items,
            selectionState = selectionState,
            footerText = footerText,
            thumbnail = thumbnail,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onThreeDotClick = onThreeDotClick,
            modifier = modifier,
        )

        FileListLayoutMode.Grid -> FileListLazyGrid(
            items = items,
            selectionState = selectionState,
            footerText = footerText,
            gridColumns = gridColumns.coerceAtLeast(1),
            thumbnail = thumbnail,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun FileListLazyColumn(
    items: List<FileListItemUiModel>,
    selectionState: FileListSelectionState,
    footerText: String?,
    thumbnail: @Composable (FileListItemUiModel) -> Bitmap?,
    onItemClick: (FileListItemUiModel) -> Unit,
    onItemLongClick: (FileListItemUiModel) -> Unit,
    onThreeDotClick: (FileListItemUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(
            items = items,
            key = { it.fileId },
        ) { item ->
            FileListLazyRow(
                item = item,
                selectionState = selectionState,
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
    selectionState: FileListSelectionState,
    footerText: String?,
    gridColumns: Int,
    thumbnail: @Composable (FileListItemUiModel) -> Bitmap?,
    onItemClick: (FileListItemUiModel) -> Unit,
    onItemLongClick: (FileListItemUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        modifier = modifier,
    ) {
        items(
            items = items,
            key = { it.fileId },
        ) { item ->
            FileListLazyGridCell(
                item = item,
                selectionState = selectionState,
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
    selectionState: FileListSelectionState,
    thumbnail: @Composable (FileListItemUiModel) -> Bitmap?,
    onItemClick: (FileListItemUiModel) -> Unit,
    onItemLongClick: (FileListItemUiModel) -> Unit,
    onThreeDotClick: (FileListItemUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayItem = item.withSelection(selectionState)
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
    selectionState: FileListSelectionState,
    thumbnail: @Composable (FileListItemUiModel) -> Bitmap?,
    onItemClick: (FileListItemUiModel) -> Unit,
    onItemLongClick: (FileListItemUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayItem = item.withSelection(selectionState)
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
 * Overlays [FileListSelectionState] onto a row/cell model for rendering.
 * When selection is active, checkbox chrome replaces the three-dot menu (non-virtual only).
 */
private fun FileListItemUiModel.withSelection(
    selection: FileListSelectionState,
): FileListItemUiModel {
    val selected = selection.isSelected(fileId)
    return if (selection.hasSelection) {
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
    val selection = rememberFileListSelectionState(
        initiallySelectedIds = setOf(1L, 3L),
    )
    HomeCloudTheme {
        Surface {
            FileList(
                items = previewItems(6),
                layoutMode = FileListLayoutMode.List,
                selectionState = selection,
                footerText = "4 files, 2 folders",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
                onItemClick = { selection.toggle(it.fileId) },
                onItemLongClick = { selection.select(it.fileId) },
            )
        }
    }
}
