package com.owncloud.android.presentation.imageedit

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import com.owncloud.android.R
import com.owncloud.android.presentation.common.compose.HomeCloudBanner
import com.owncloud.android.presentation.common.compose.HomeCloudBannerStyle
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudSlider
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ImageCropRotateScreen(
    imageUri: Uri?,
    uiState: ImageCropRotateUiState,
    errorMessage: String?,
    cropHandle: CropImageViewHandle,
    onErrorDismissed: () -> Unit,
    onImageLoaded: (success: Boolean) -> Unit,
    onCropComplete: (File?, Exception?) -> Unit,
    onOverwriteChosen: () -> Unit,
    onSaveAsCopyChosen: () -> Unit,
    onConflictDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rotationDegrees by rememberSaveable { mutableIntStateOf(0) }

    ImageCropRotateScreenView(
        imageUri = imageUri,
        uiState = uiState,
        errorMessage = errorMessage,
        rotationDegrees = rotationDegrees,
        cropHandle = cropHandle,
        onErrorDismissed = onErrorDismissed,
        onRotate90 = { rotationDegrees = (MAX_ROTATION_DEGREES + rotationDegrees + ROTATION_STEP_90) % FULL_CIRCLE_DEGREES - MAX_ROTATION_DEGREES },
        onRotationChange = { rotationDegrees = it.coerceIn(-MAX_ROTATION_DEGREES, MAX_ROTATION_DEGREES) },
        onImageLoaded = onImageLoaded,
        onCropComplete = onCropComplete,
        onOverwriteChosen = onOverwriteChosen,
        onSaveAsCopyChosen = onSaveAsCopyChosen,
        onConflictDismissed = onConflictDismissed,
        modifier = modifier,
    )
}

@Composable
internal fun ImageCropRotateScreenView(
    imageUri: Uri?,
    uiState: ImageCropRotateUiState,
    errorMessage: String?,
    rotationDegrees: Int,
    cropHandle: CropImageViewHandle,
    onErrorDismissed: () -> Unit,
    onRotate90: () -> Unit,
    onRotationChange: (Int) -> Unit,
    onImageLoaded: (success: Boolean) -> Unit,
    onCropComplete: (File?, Exception?) -> Unit,
    onOverwriteChosen: () -> Unit,
    onSaveAsCopyChosen: () -> Unit,
    onConflictDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controlsEnabled = uiState is ImageCropRotateUiState.Ready

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black),
        ) {
            if (imageUri != null && uiState !is ImageCropRotateUiState.Downloading) {
                ImageCropRotateCropHost(
                    imageUri = imageUri,
                    rotationDegrees = rotationDegrees,
                    handle = cropHandle,
                    onImageLoaded = onImageLoaded,
                    onCropComplete = onCropComplete,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (uiState is ImageCropRotateUiState.Downloading) {
                ImageCropRotateDownloadProgress(
                    progressPercent = uiState.progress,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(dimensionResource(R.dimen.standard_margin)),
                )
            }
            if (uiState is ImageCropRotateUiState.Saving) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        if (errorMessage != null) {
            HomeCloudBanner(
                message = errorMessage,
                style = HomeCloudBannerStyle.ERROR,
                onDismiss = onErrorDismissed,
                modifier = Modifier.padding(dimensionResource(R.dimen.standard_half_margin)),
            )
        }
        ImageCropRotateControls(
            rotationDegrees = rotationDegrees,
            enabled = controlsEnabled,
            onRotate90 = onRotate90,
            onRotationChange = onRotationChange,
        )
    }
    if (uiState is ImageCropRotateUiState.NameConflict) {
        ImageCropRotateNameConflictDialog(
            fileName = uiState.existingFileName,
            onOverwrite = onOverwriteChosen,
            onSaveAsCopy = onSaveAsCopyChosen,
            onDismiss = onConflictDismissed,
        )
    }
}

@Composable
private fun ImageCropRotateControls(
    rotationDegrees: Int,
    enabled: Boolean,
    onRotate90: () -> Unit,
    onRotationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimensionResource(R.dimen.standard_margin)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onRotate90,
                enabled = enabled,
            ) {
                Text(text = stringResource(R.string.homecloud_imageedit_rotate))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.homecloud_imageedit_angle, rotationDegrees),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        HomeCloudSlider(
            value = rotationDegrees.toFloat(),
            onValueChange = { onRotationChange(it.roundToInt()) },
            valueRange = -MAX_ROTATION_DEGREES.toFloat()..MAX_ROTATION_DEGREES.toFloat(),
            steps = ROTATION_SLIDER_STEPS,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ImageCropRotateNameConflictDialog(
    fileName: String,
    onOverwrite: () -> Unit,
    onSaveAsCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.file_already_exists)) },
        text = { Text(text = stringResource(R.string.file_already_exists_description, fileName)) },
        confirmButton = {
            TextButton(onClick = onOverwrite) {
                Text(text = stringResource(R.string.conflict_replace))
            }
        },
        dismissButton = {
            TextButton(onClick = onSaveAsCopy) {
                Text(text = stringResource(R.string.conflict_keep_both))
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun ImageCropRotateDownloadProgress(
    progressPercent: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.homecloud_imageedit_download_in_progress),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.standard_margin)))
        if (progressPercent < 0) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { progressPercent.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private data class ImageCropRotateScreenPreviewModel(
    val uiState: ImageCropRotateUiState,
    val errorMessage: String? = null,
    val rotationDegrees: Int = 0,
)

private class ImageCropRotateScreenPreviewParameterProvider :
    CollectionPreviewParameterProvider<ImageCropRotateScreenPreviewModel>(
        listOf(
            ImageCropRotateScreenPreviewModel(
                uiState = ImageCropRotateUiState.Ready(localFilePath = PREVIEW_LOCAL_PATH),
                rotationDegrees = 15,
            ),
            ImageCropRotateScreenPreviewModel(
                uiState = ImageCropRotateUiState.Downloading(progress = 40),
            ),
            ImageCropRotateScreenPreviewModel(
                uiState = ImageCropRotateUiState.NameConflict(
                    localFilePath = PREVIEW_LOCAL_PATH,
                    existingFileName = PREVIEW_FILE_NAME,
                    tempOutputFile = File(PREVIEW_FILE_NAME),
                ),
            ),
        ),
    )

@HomeCloudPreview
@Composable
private fun ImageCropRotateScreenPreview(
    @PreviewParameter(ImageCropRotateScreenPreviewParameterProvider::class)
    model: ImageCropRotateScreenPreviewModel,
) {
    HomeCloudTheme {
        Surface {
            ImageCropRotateScreenView(
                imageUri = null,
                uiState = model.uiState,
                errorMessage = model.errorMessage,
                rotationDegrees = model.rotationDegrees,
                cropHandle = previewCropHandle,
                onErrorDismissed = {},
                onRotate90 = {},
                onRotationChange = {},
                onImageLoaded = {},
                onCropComplete = { _, _ -> },
                onOverwriteChosen = {},
                onSaveAsCopyChosen = {},
                onConflictDismissed = {},
            )
        }
    }
}

private val previewCropHandle = CropImageViewHandle()

private const val ROTATION_STEP_90 = 90
private const val FULL_CIRCLE_DEGREES = 360
private const val MAX_ROTATION_DEGREES = 180
private const val ROTATION_SLIDER_STEPS = 360
private const val PREVIEW_LOCAL_PATH = "/preview"
private const val PREVIEW_FILE_NAME = "photo.png"
