package com.owncloud.android.presentation.imageedit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber

class ImageCropRotateActivity : AppCompatActivity() {

    private val viewModel: ImageCropRotateViewModel by viewModel {
        parametersOf(intent.getParcelableExtra(EXTRA_FILE) as OCFile?)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val file = intent.getParcelableExtra(EXTRA_FILE) as OCFile?
        if (file == null) {
            Timber.w("Cannot edit image, missing file extra")
            finish()
            return
        }

        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) {
            onEditCancelled()
        }

        setContent {
            HomeCloudTheme {
                val uiState by viewModel.uiState.collectAsState()
                val imageUri = remember(uiState.localFilePath) {
                    viewModel.getInputUri(this@ImageCropRotateActivity)
                }

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
                        isDownloading = uiState.isDownloading,
                        downloadProgress = uiState.downloadProgress,
                        isImageLoaded = uiState.isImageLoaded,
                        errorMessage = uiState.errorMessageRes?.let { stringResource(it) },
                        onErrorDismissed = viewModel::consumeError,
                        onCancel = ::onEditCancelled,
                        onImageLoaded = viewModel::onImageLoaded,
                        onCropComplete = viewModel::onSaveCompleted,
                        onSaveRequested = ::onSaveRequested,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    private fun onEditCancelled() {
        viewModel.cancelDownloadIfNeeded()
        finish()
    }

    private fun onSaveRequested(handle: CropImageViewHandle) {
        if (handle.view == null) return
        viewModel.onSaveStarted()
        val outputFile = viewModel.createOutputFile()
        val outputUri = viewModel.getOutputUri(this, outputFile)
        handle.cropToJpeg(outputUri, outputFile)
    }

    companion object {
        const val EXTRA_FILE = "IMAGE_CROP_ROTATE_FILE"
        const val EXTRA_OUTPUT_PATH = "IMAGE_CROP_ROTATE_OUTPUT_PATH"

        fun createIntent(context: Context, file: OCFile): Intent =
            Intent(context, ImageCropRotateActivity::class.java)
                .putExtra(EXTRA_FILE, file)
    }
}
