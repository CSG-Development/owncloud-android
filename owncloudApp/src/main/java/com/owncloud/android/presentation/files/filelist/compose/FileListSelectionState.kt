package com.owncloud.android.presentation.files.filelist.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudTheme

/**
 * Id-based selection holder for the Compose file list.
 * Callers pass only selectable file ids ([selectAll] / [inverse]); the footer has no id and is excluded.
 */
@Stable
class FileListSelectionState(
    initiallySelectedIds: Set<Long> = emptySet(),
) {
    var selectedIds by mutableStateOf(initiallySelectedIds)
        private set

    val selectedItemCount: Int
        get() = selectedIds.size

    val hasSelection: Boolean
        get() = selectedIds.isNotEmpty()

    fun isSelected(fileId: Long): Boolean = fileId in selectedIds

    fun select(fileId: Long) {
        if (fileId !in selectedIds) {
            selectedIds = selectedIds + fileId
        }
    }

    fun deselect(fileId: Long) {
        if (fileId in selectedIds) {
            selectedIds = selectedIds - fileId
        }
    }

    fun toggle(fileId: Long) {
        selectedIds = if (fileId in selectedIds) {
            selectedIds - fileId
        } else {
            selectedIds + fileId
        }
    }

    fun clear() {
        if (selectedIds.isNotEmpty()) {
            selectedIds = emptySet()
        }
    }

    /** Replaces selection with [fileIds]. Footer / non-file rows must not be included. */
    fun selectAll(fileIds: Collection<Long>) {
        selectedIds = fileIds.toSet()
    }

    /**
     * Inverts selection among [fileIds] only: selected ↔ unselected for those ids.
     * Ids outside [fileIds] are dropped from the selection.
     */
    fun inverse(fileIds: Collection<Long>) {
        val current = selectedIds
        selectedIds = fileIds.filterTo(mutableSetOf()) { it !in current }
    }
}

@Composable
fun rememberFileListSelectionState(
    initiallySelectedIds: Set<Long> = emptySet(),
): FileListSelectionState = remember {
    FileListSelectionState(initiallySelectedIds)
}

@HomeCloudPreview
@Composable
private fun FileListSelectionStatePreview() {
    val selection = rememberFileListSelectionState(
        initiallySelectedIds = setOf(FileListItemUiModelFixtures.file.fileId),
    )
    val items = listOf(
        FileListItemUiModelFixtures.folder,
        FileListItemUiModelFixtures.file,
        FileListItemUiModelFixtures.longName,
    )
    val inSelectionMode = selection.hasSelection

    HomeCloudTheme {
        Column {
            items.forEach { item ->
                val selected = selection.isSelected(item.fileId)
                FileListRow(
                    item = item.copy(
                        isSelected = selected,
                        showCheckbox = inSelectionMode,
                        showThreeDotMenu = !inSelectionMode,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (inSelectionMode) {
                            selection.toggle(item.fileId)
                        }
                    },
                    onLongClick = { selection.select(item.fileId) },
                )
            }
        }
    }
}
