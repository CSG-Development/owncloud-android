package com.owncloud.android.presentation.common.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.owncloud.android.R

/**
 * Compose [AlertDialog] styled to match XML [R.style.Theme_homeCloud_AlertDialog].
 */
@Composable
fun HomeCloudDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val contentColor = colorResource(R.color.homecloud_primary)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { HomeCloudDialogTitle(title = title) },
        text = { HomeCloudDialogMessage(message = text) },
        confirmButton = {
            HomeCloudDialogButton(
                label = confirmLabel,
                onClick = onConfirm,
            )
        },
        dismissButton = if (dismissLabel != null && onDismiss != null) {
            {
                HomeCloudDialogButton(
                    label = dismissLabel,
                    onClick = onDismiss,
                )
            }
        } else {
            null
        },
        modifier = modifier,
        shape = RoundedCornerShape(DIALOG_CORNER_RADIUS),
        containerColor = colorResource(R.color.homecloud_dialog_background),
        titleContentColor = contentColor,
        textContentColor = contentColor,
        tonalElevation = 0.dp,
    )
}

@Composable
private fun HomeCloudDialogTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier,
        style = MaterialTheme.typography.headlineSmall,
    )
}

@Composable
private fun HomeCloudDialogMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun HomeCloudDialogButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(
            contentColor = colorResource(R.color.homecloud_dialog_button_enabled),
            disabledContentColor = colorResource(R.color.homecloud_dialog_button_disabled),
        ),
    ) {
        Text(text = label)
    }
}

private val DIALOG_CORNER_RADIUS = 16.dp

private data class HomeCloudDialogPreviewModel(
    val showDismissButton: Boolean = false,
)

private class HomeCloudDialogPreviewParameterProvider :
    CollectionPreviewParameterProvider<HomeCloudDialogPreviewModel>(
        listOf(
            HomeCloudDialogPreviewModel(),
            HomeCloudDialogPreviewModel(showDismissButton = true),
        ),
    )

@HomeCloudPreview
@Composable
private fun HomeCloudDialogPreview(
    @PreviewParameter(HomeCloudDialogPreviewParameterProvider::class)
    model: HomeCloudDialogPreviewModel,
) {
    HomeCloudTheme {
        HomeCloudDialog(
            onDismissRequest = {},
            title = stringResource(R.string.file_already_exists),
            text = stringResource(R.string.file_already_exists_description, PREVIEW_FILE_NAME),
            confirmLabel = stringResource(R.string.homecloud_ok),
            onConfirm = {},
            dismissLabel = stringResource(R.string.common_no).takeIf { model.showDismissButton },
            onDismiss = {}.takeIf { model.showDismissButton },
        )
    }
}

private const val PREVIEW_FILE_NAME = "photo.png"
