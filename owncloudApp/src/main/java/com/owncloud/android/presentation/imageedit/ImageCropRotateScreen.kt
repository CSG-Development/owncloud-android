package com.owncloud.android.presentation.imageedit

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.owncloud.android.R
import com.owncloud.android.presentation.common.compose.HomeCloudBanner
import com.owncloud.android.presentation.common.compose.HomeCloudBannerStyle
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ImageCropRotateScreen(
    imageUri: Uri?,
    uiState: ImageCropRotateUiState,
    errorMessage: String?,
    onErrorDismissed: () -> Unit,
    onCancel: () -> Unit,
    onImageLoaded: (success: Boolean) -> Unit,
    onCropComplete: (File?, Exception?) -> Unit,
    onSaveRequested: (CropImageViewHandle) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rotationDegrees by rememberSaveable { mutableIntStateOf(0) }
    val cropHandle = remember { CropImageViewHandle() }
    val controlsEnabled = uiState is ImageCropRotateUiState.Ready

    Column(
        modifier = modifier.windowInsetsPadding(WindowInsets.systemBars),
    ) {
        ImageCropRotateTopBar(
            isDoneEnabled = controlsEnabled,
            onCancel = onCancel,
            onDone = { onSaveRequested(cropHandle) },
        )
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
            onRotate90 = { rotationDegrees = (rotationDegrees + ROTATION_STEP_90) % FULL_CIRCLE_DEGREES },
            onRotationChange = { rotationDegrees = it.coerceIn(0, MAX_ROTATION_DEGREES) },
        )
    }
}

@Composable
private fun ImageCropRotateTopBar(
    isDoneEnabled: Boolean,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimensionResource(R.dimen.standard_half_margin)),
    ) {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Text(text = stringResource(R.string.homecloud_imageedit_cancel))
        }
        Text(
            text = stringResource(R.string.homecloud_imageedit_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center),
        )
        TextButton(
            onClick = onDone,
            enabled = isDoneEnabled,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Text(text = stringResource(R.string.homecloud_imageedit_done))
        }
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
        Slider(
            value = rotationDegrees.toFloat(),
            onValueChange = { onRotationChange(it.roundToInt()) },
            valueRange = 0f..MAX_ROTATION_DEGREES.toFloat(),
            steps = ROTATION_SLIDER_STEPS,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
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

@HomeCloudPreview
@Composable
private fun ImageCropRotateChromePreview() {
    HomeCloudTheme {
        Surface {
            Column(modifier = Modifier.fillMaxWidth()) {
                ImageCropRotateTopBar(
                    isDoneEnabled = true,
                    onCancel = {},
                    onDone = {},
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.DarkGray)
                        .padding(dimensionResource(R.dimen.standard_margin)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Image",
                        color = Color.White,
                    )
                }
                ImageCropRotateControls(
                    rotationDegrees = 15,
                    enabled = true,
                    onRotate90 = {},
                    onRotationChange = {},
                )
            }
        }
    }
}

@HomeCloudPreview
@Composable
private fun ImageCropRotateDownloadProgressPreview() {
    HomeCloudTheme {
        Surface {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(dimensionResource(R.dimen.standard_margin)),
            ) {
                ImageCropRotateDownloadProgress(
                    progressPercent = 40,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private const val ROTATION_STEP_90 = 90
private const val FULL_CIRCLE_DEGREES = 360
private const val MAX_ROTATION_DEGREES = 359
private const val ROTATION_SLIDER_STEPS = 358
