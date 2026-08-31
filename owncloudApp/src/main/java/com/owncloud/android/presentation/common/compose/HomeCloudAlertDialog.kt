package com.owncloud.android.presentation.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.owncloud.android.R

/**
 * Branded alert content for use inside a platform dialog host
 * (e.g. [com.google.android.material.dialog.MaterialAlertDialogBuilder.setView]).
 */
@Composable
fun HomeCloudAlertDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = DIALOG_BORDER_WIDTH,
                color = colorResource(R.color.homecloud_dialog_accent_border),
                shape = RoundedCornerShape(DIALOG_CORNER_RADIUS),
            )
            .clip(RoundedCornerShape(DIALOG_CORNER_RADIUS))
            .background(colorResource(R.color.homecloud_dialog_background))
    ) {
        HomeCloudAlertDialogTitle(title = title)
        HomeCloudAlertDialogMessage(message = message)
        TextButton(
            onClick = onConfirm,
            modifier = Modifier
                .align(Alignment.End)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = colorResource(R.color.homecloud_dialog_button_enabled),
            ),
        ) {
            Text(text = confirmLabel)
        }
    }
}

@Composable
private fun HomeCloudAlertDialogTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .background(colorResource(R.color.homecloud_dialog_title_background))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        color = colorResource(R.color.homecloud_primary),
        style = MaterialTheme.typography.headlineSmall,
    )
}

@Composable
private fun HomeCloudAlertDialogMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        color = colorResource(R.color.homecloud_primary),
        style = MaterialTheme.typography.bodyMedium,
    )
}

private val DIALOG_CORNER_RADIUS = 16.dp
private val DIALOG_BORDER_WIDTH = 1.dp

@HomeCloudPreview
@Composable
private fun HomeCloudAlertDialogPreview() {
    HomeCloudTheme {
        HomeCloudAlertDialog(
            title = stringResource(R.string.homecloud_filelist_unsupported_archive_title),
            message = stringResource(R.string.homecloud_filelist_unsupported_archive_message),
            confirmLabel = stringResource(R.string.homecloud_ok),
            onConfirm = {},
        )
    }
}
