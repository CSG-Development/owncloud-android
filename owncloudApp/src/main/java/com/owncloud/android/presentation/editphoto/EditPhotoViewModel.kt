package com.owncloud.android.presentation.editphoto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.usecases.GetFileByIdUseCase
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

sealed interface EditPhotoUiState {
    data object Loading : EditPhotoUiState

    data class Ready(
        val localFilePath: String,
        val isImageLoaded: Boolean = false,
        val errorMessageRes: Int? = null,
    ) : EditPhotoUiState

    data class Unavailable(
        val errorMessageRes: Int? = null,
    ) : EditPhotoUiState
}

fun EditPhotoUiState.localFilePath(): String? = (this as? EditPhotoUiState.Ready)?.localFilePath

fun EditPhotoUiState.errorMessageRes(): Int? = when (this) {
    is EditPhotoUiState.Ready -> errorMessageRes
    is EditPhotoUiState.Unavailable -> errorMessageRes
    EditPhotoUiState.Loading -> null
}

class EditPhotoViewModel(
    private val getFileByIdUseCase: GetFileByIdUseCase,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    initialFile: OCFile,
) : ViewModel() {

    private var ocFile: OCFile = initialFile

    private val _uiState = MutableStateFlow<EditPhotoUiState>(EditPhotoUiState.Loading)
    val uiState: StateFlow<EditPhotoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prepareInput()
        }
    }

    fun onImageLoaded(success: Boolean) {
        _uiState.update { current ->
            val ready = current as? EditPhotoUiState.Ready ?: return@update current
            if (success) {
                ready.copy(isImageLoaded = true, errorMessageRes = null)
            } else {
                ready.copy(
                    isImageLoaded = false,
                    errorMessageRes = R.string.homecloud_editphoto_load_error,
                )
            }
        }
    }

    fun consumeError() {
        _uiState.update { current ->
            when (current) {
                is EditPhotoUiState.Ready -> current.copy(errorMessageRes = null)
                is EditPhotoUiState.Unavailable -> current.copy(errorMessageRes = null)
                EditPhotoUiState.Loading -> current
            }
        }
    }

    private suspend fun prepareInput() {
        ocFile.id?.let { fileId ->
            withContext(coroutinesDispatcherProvider.io) {
                getFileByIdUseCase(GetFileByIdUseCase.Params(fileId)).getDataOrNull()
            }?.let { ocFile = it }
        }

        val localPath = ocFile.storagePath
        val localFile = localPath?.takeIf { it.isNotBlank() }?.let(::File)
        if (ocFile.isAvailableLocally && localFile != null && localFile.exists() && localFile.canRead()) {
            _uiState.update { EditPhotoUiState.Ready(localFilePath = localFile.absolutePath) }
        } else {
            Timber.w("Cannot edit photo, file is not available locally: %s", ocFile.storagePath)
            _uiState.update {
                EditPhotoUiState.Unavailable(errorMessageRes = R.string.homecloud_editphoto_unavailable)
            }
        }
    }
}
