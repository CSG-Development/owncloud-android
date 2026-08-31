package com.owncloud.android.presentation.files.filelist.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owncloud.android.R
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudTheme

/**
 * Overflow menu for an upload virtual file row/cell.
 * Caller owns the anchor [Box]; place this as a child so the menu appears next to the item.
 *
 * Styled to match XML [R.drawable.bg_popup_menu] / Theme.homeCloud popup menu
 * (homecloud_menu_background, 16dp corners, primary text).
 */
@Composable
fun FileListVirtualUploadMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    showCancelUpload: Boolean,
    onOpenUploads: () -> Unit,
    onCancelUpload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryText = MaterialTheme.colorScheme.primary
    val itemColors = MenuDefaults.itemColors(textColor = primaryText)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = colorResource(R.color.homecloud_menu_background),
        tonalElevation = 0.dp,
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.uploads_view_title),
                    color = primaryText,
                    fontSize = 16.sp,
                )
            },
            onClick = onOpenUploads,
            colors = itemColors,
        )
        if (showCancelUpload) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.homecloud_filelist_virtual_file_cancel_upload),
                        color = primaryText,
                        fontSize = 16.sp,
                    )
                },
                onClick = onCancelUpload,
                colors = itemColors,
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun FileListVirtualUploadMenuPreview() {
    HomeCloudTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimensionResource(R.dimen.item_file_list_min_height)),
            ) {
                FileListVirtualUploadMenu(
                    expanded = true,
                    onDismissRequest = {},
                    showCancelUpload = true,
                    onOpenUploads = {},
                    onCancelUpload = {},
                )
            }
        }
    }
}

@HomeCloudPreview
@Composable
private fun FileListVirtualUploadMenuCompletedPreview() {
    HomeCloudTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimensionResource(R.dimen.item_file_list_min_height)),
            ) {
                FileListVirtualUploadMenu(
                    expanded = true,
                    onDismissRequest = {},
                    showCancelUpload = false,
                    onOpenUploads = {},
                    onCancelUpload = {},
                )
            }
        }
    }
}
