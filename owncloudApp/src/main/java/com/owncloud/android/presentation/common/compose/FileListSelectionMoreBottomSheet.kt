package com.owncloud.android.presentation.common.compose

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.owncloud.android.R
import com.owncloud.android.domain.appregistry.model.AppRegistryProvider
import com.owncloud.android.domain.files.model.FileMenuOption
import com.owncloud.android.extensions.toResId
import com.owncloud.android.extensions.toStringResId

/**
 * "More" sheet content for a platform [com.google.android.material.bottomsheet.BottomSheetDialog] host.
 * Options in [toolbarOptions] stay in the toolbar and are omitted from the sheet.
 */
@Composable
fun FileListSelectionMoreBottomSheet(
    menuOptions: List<FileMenuOption>,
    hasWritePermission: Boolean,
    onAction: (menuId: Int) -> Unit,
    modifier: Modifier = Modifier,
    toolbarOptions: Set<FileMenuOption> = DEFAULT_SELECTION_TOOLBAR_OPTIONS,
    openInWebProviders: List<AppRegistryProvider> = emptyList(),
    onOpenInWeb: (providerName: String) -> Unit = {},
) {
    val rows = selectionMoreSheetRows(
        context = LocalContext.current,
        menuOptions = menuOptions,
        hasWritePermission = hasWritePermission,
        toolbarOptions = toolbarOptions,
        openInWebProviders = openInWebProviders,
        onAction = onAction,
        onOpenInWeb = onOpenInWeb,
    )
    val maxHeight = dimensionResource(R.dimen.max_height_bottom_sheet)

    Column(
        modifier = modifier,
    ) {
        SelectionMoreSheetDragHandle(modifier = Modifier.fillMaxWidth())
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .nestedScroll(rememberNestedScrollInteropConnection())
                .verticalScroll(rememberScrollState()),
        ) {
            rows.forEach { row ->
                key(row.key) {
                    SelectionMoreSheetRow(
                        title = row.title,
                        onClick = row.onClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionMoreSheetDragHandle(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(top = DRAG_HANDLE_PADDING_TOP, bottom = DRAG_HANDLE_PADDING_BOTTOM),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(DRAG_HANDLE_WIDTH)
                .height(DRAG_HANDLE_HEIGHT)
                .clip(RoundedCornerShape(DRAG_HANDLE_CORNER_RADIUS))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun SelectionMoreSheetRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = dimensionResource(R.dimen.standard_padding)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun selectionMoreSheetRows(
    context: Context,
    menuOptions: List<FileMenuOption>,
    hasWritePermission: Boolean,
    toolbarOptions: Set<FileMenuOption>,
    openInWebProviders: List<AppRegistryProvider>,
    onAction: (menuId: Int) -> Unit,
    onOpenInWeb: (providerName: String) -> Unit,
): List<SelectionMoreSheetRowModel> {
    val rows = mutableListOf<SelectionMoreSheetRowModel>()
    var openInWebAdded = false

    menuOptions.filterNot { it in toolbarOptions }.forEach { menuOption ->
        val title = if (menuOption == FileMenuOption.OPEN_WITH && !hasWritePermission) {
            context.getString(R.string.actionbar_open_with_read_only)
        } else {
            context.getString(menuOption.toStringResId())
        }
        rows += SelectionMoreSheetRowModel(
            key = "menu_${menuOption.name}",
            title = title,
            onClick = { onAction(menuOption.toResId()) },
        )
        if (menuOption == FileMenuOption.OPEN_WITH) {
            rows += openInWebRowModels(context, openInWebProviders, onOpenInWeb)
            openInWebAdded = true
        }
    }

    if (!openInWebAdded) {
        rows += openInWebRowModels(context, openInWebProviders, onOpenInWeb)
    }
    return rows
}

private fun openInWebRowModels(
    context: Context,
    providers: List<AppRegistryProvider>,
    onOpenInWeb: (providerName: String) -> Unit,
): List<SelectionMoreSheetRowModel> =
    providers.map { provider ->
        SelectionMoreSheetRowModel(
            key = "open_in_web_${provider.name}",
            title = context.getString(R.string.ic_action_open_with_web, provider.name),
            onClick = { onOpenInWeb(provider.name) },
        )
    }

private data class SelectionMoreSheetRowModel(
    val key: String,
    val title: String,
    val onClick: () -> Unit,
)

val DEFAULT_SELECTION_TOOLBAR_OPTIONS = setOf(FileMenuOption.SHARE, FileMenuOption.SYNC)

val PREVIEW_FILE_ACTIONS_TOOLBAR_OPTIONS = setOf(
    FileMenuOption.SHARE,
    FileMenuOption.SYNC,
    FileMenuOption.DOWNLOAD,
    FileMenuOption.CANCEL_SYNC,
)

fun hasFileActionsSheetRows(
    menuOptions: List<FileMenuOption>,
    toolbarOptions: Set<FileMenuOption> = DEFAULT_SELECTION_TOOLBAR_OPTIONS,
    openInWebProviders: List<AppRegistryProvider> = emptyList(),
): Boolean =
    menuOptions.any { it !in toolbarOptions } || openInWebProviders.isNotEmpty()

private val DRAG_HANDLE_WIDTH = 40.dp
private val DRAG_HANDLE_HEIGHT = 2.dp
private val DRAG_HANDLE_CORNER_RADIUS = 4.dp
private val DRAG_HANDLE_PADDING_TOP = 8.dp
private val DRAG_HANDLE_PADDING_BOTTOM = 24.dp

@HomeCloudPreview
@Composable
private fun FileListSelectionMoreBottomSheetPreview() {
    HomeCloudTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            FileListSelectionMoreBottomSheet(
                menuOptions = listOf(
                    FileMenuOption.OPEN_WITH,
                    FileMenuOption.DOWNLOAD,
                    FileMenuOption.MOVE,
                    FileMenuOption.COPY,
                    FileMenuOption.REMOVE,
                    FileMenuOption.SHARE,
                    FileMenuOption.SYNC,
                ),
                hasWritePermission = false,
                onAction = {},
                openInWebProviders = listOf(
                    AppRegistryProvider(name = "Collabora", icon = ""),
                    AppRegistryProvider(name = "OnlyOffice", icon = ""),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
