package com.owncloud.android.presentation.imageedit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber
import java.io.File

class ImageCropRotateActivity : AppCompatActivity() {

    private val viewModel: ImageCropRotateViewModel by viewModel {
        parametersOf(intent.getStringExtra(EXTRA_INPUT_PATH).orEmpty())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!viewModel.isInputValid()) {
            Timber.w("Cannot edit image, invalid input path: %s", viewModel.inputFilePath)
            viewModel.onImageLoaded(false)
        }

        val imageUri = viewModel.getInputUri(this)

        setContent {
            HomeCloudTheme {
                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(uiState.outputFile) {
                    val outputFile = uiState.outputFile ?: return@LaunchedEffect
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(EXTRA_OUTPUT_PATH, outputFile.absolutePath),
                    )
                    finish()
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    ImageCropRotateScreen(
                        imageUri = imageUri,
                        isSaving = uiState.isSaving,
                        isImageLoaded = uiState.isImageLoaded,
                        errorMessage = uiState.errorMessageRes?.let { stringResource(it) },
                        onErrorDismissed = viewModel::consumeError,
                        onCancel = ::finish,
                        onImageLoaded = viewModel::onImageLoaded,
                        onCropComplete = viewModel::onSaveCompleted,
                        onSaveRequested = ::onSaveRequested,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    private fun onSaveRequested(handle: CropImageViewHandle) {
        if (handle.view == null) return
        viewModel.onSaveStarted()
        val outputFile = viewModel.createOutputFile()
        val outputUri = viewModel.getOutputUri(this, outputFile)
        handle.cropToJpeg(outputUri, outputFile)
    }

    companion object {
        const val EXTRA_INPUT_PATH = "IMAGE_CROP_ROTATE_INPUT_PATH"
        const val EXTRA_OUTPUT_PATH = "IMAGE_CROP_ROTATE_OUTPUT_PATH"

        fun createIntent(context: Context, inputFile: File): Intent =
            Intent(context, ImageCropRotateActivity::class.java)
                .putExtra(EXTRA_INPUT_PATH, inputFile.absolutePath)
    }
}
