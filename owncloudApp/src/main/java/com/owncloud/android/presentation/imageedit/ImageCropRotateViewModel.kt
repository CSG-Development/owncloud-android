package com.owncloud.android.presentation.imageedit

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import com.owncloud.android.R
import com.owncloud.android.providers.ContextProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.io.File
import java.util.UUID

data class ImageCropRotateUiState(
    val isSaving: Boolean = false,
    val isImageLoaded: Boolean = false,
    val errorMessageRes: Int? = null,
    val outputFile: File? = null,
)

class ImageCropRotateViewModel(
    private val contextProvider: ContextProvider,
    val inputFilePath: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageCropRotateUiState())
    val uiState: StateFlow<ImageCropRotateUiState> = _uiState.asStateFlow()

    val inputFile: File = File(inputFilePath)

    fun isInputValid(): Boolean = inputFile.exists() && inputFile.canRead()

    fun getInputUri(context: Context): Uri? {
        if (!isInputValid()) return null
        return uriForFile(context, inputFile)
    }

    fun getOutputUri(context: Context, outputFile: File): Uri = uriForFile(context, outputFile)

    fun createOutputFile(): File {
        val dir = File(contextProvider.getContext().cacheDir, OUTPUT_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "${UUID.randomUUID()}.jpg").also { file ->
            file.createNewFile()
        }
    }

    fun onImageLoaded(success: Boolean) {
        _uiState.update {
            it.copy(
                isImageLoaded = success,
                errorMessageRes = if (success) null else R.string.homecloud_imageedit_load_error,
            )
        }
    }

    fun onSaveStarted() {
        _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }
    }

    fun onSaveCompleted(outputFile: File?, error: Exception?) {
        if (error != null) {
            Timber.e(error, "Failed to crop and rotate image")
        }
        val savedSuccessfully = error == null && outputFile != null && outputFile.exists() && outputFile.length() > 0
        _uiState.update {
            if (savedSuccessfully) {
                it.copy(isSaving = false, outputFile = outputFile, errorMessageRes = null)
            } else {
                it.copy(isSaving = false, errorMessageRes = R.string.homecloud_imageedit_save_error)
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessageRes = null) }
    }

    private fun uriForFile(context: Context, file: File): Uri {
        return try {
            FileProvider.getUriForFile(
                context,
                context.getString(R.string.file_provider_authority),
                file,
            )
        } catch (illegalArgument: IllegalArgumentException) {
            Timber.w(illegalArgument, "Falling back to file URI for %s", file.absolutePath)
            Uri.fromFile(file)
        }
    }

    companion object {
        const val OUTPUT_DIR = "image_edit"
    }
}
