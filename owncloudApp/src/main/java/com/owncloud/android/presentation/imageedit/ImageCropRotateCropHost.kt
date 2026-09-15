package com.owncloud.android.presentation.imageedit

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.canhub.cropper.CropImageView
import java.io.File

class CropImageViewHandle {
    var view: CropImageView? = null
        internal set
    var pendingOutputFile: File? = null
}

@Composable
fun ImageCropRotateCropHost(
    imageUri: Uri,
    rotationDegrees: Int,
    handle: CropImageViewHandle,
    onImageLoaded: (success: Boolean) -> Unit,
    onCropComplete: (File?, Exception?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onImageLoadedState = rememberUpdatedState(onImageLoaded)
    val onCropCompleteState = rememberUpdatedState(onCropComplete)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                addView(
                    CropImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        )
                        guidelines = CropImageView.Guidelines.ON
                        setFixedAspectRatio(false)
                        setBackgroundColor(Color.BLACK)
                        handle.view = this
                        setOnSetImageUriCompleteListener { _, _, error ->
                            onImageLoadedState.value(error == null)
                        }
                        setOnCropImageCompleteListener { _, result ->
                            val outputFile = handle.pendingOutputFile
                            val success = result.isSuccessful && outputFile != null && outputFile.exists()
                            onCropCompleteState.value(
                                if (success) outputFile else null,
                                result.error,
                            )
                        }
                    },
                )
            }
        },
        update = { container ->
            val view = container.cropImageView()
            if (imageUri != view.imageUri) {
                view.setImageUriAsync(imageUri)
            }
            if (view.rotatedDegrees != rotationDegrees) {
                view.rotatedDegrees = rotationDegrees
            }
        },
        onRelease = {
            handle.view = null
            handle.pendingOutputFile = null
        },
    )
}

fun CropImageViewHandle.cropToOutput(
    format: Bitmap.CompressFormat,
    outputUri: Uri,
    outputFile: File,
) {
    val cropView = view ?: return
    pendingOutputFile = outputFile
    cropView.croppedImageAsync(
        saveCompressFormat = format,
        saveCompressQuality = OUTPUT_QUALITY,
        customOutputUri = outputUri,
    )
}

private const val OUTPUT_QUALITY = 90

private fun FrameLayout.cropImageView(): CropImageView = getChildAt(0) as CropImageView
