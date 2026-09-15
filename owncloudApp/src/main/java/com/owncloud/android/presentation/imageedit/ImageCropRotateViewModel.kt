package com.owncloud.android.presentation.imageedit

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.usecases.GetFileByIdUseCase
import com.owncloud.android.extensions.getTagsForDownload
import com.owncloud.android.extensions.getWorkInfoByTags
import com.owncloud.android.providers.ContextProvider
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import com.owncloud.android.usecases.transfers.downloads.CancelDownloadForFileUseCase
import com.owncloud.android.usecases.transfers.downloads.DownloadFileUseCase
import com.owncloud.android.workers.DownloadFileWorker.Companion.WORKER_KEY_PROGRESS
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID

internal const val INDETERMINATE_DOWNLOAD_PROGRESS = -1

data class ImageCropRotateUiState(
    val isSaving: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = INDETERMINATE_DOWNLOAD_PROGRESS,
    val localFilePath: String? = null,
    val isImageLoaded: Boolean = false,
    val errorMessageRes: Int? = null,
    val outputFile: File? = null,
)

class ImageCropRotateViewModel(
    private val contextProvider: ContextProvider,
    private val getFileByIdUseCase: GetFileByIdUseCase,
    private val downloadFileUseCase: DownloadFileUseCase,
    private val cancelDownloadForFileUseCase: CancelDownloadForFileUseCase,
    private val workManager: WorkManager,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    initialFile: OCFile,
) : ViewModel() {

    private var ocFile: OCFile = initialFile

    private val _uiState = MutableStateFlow(
        ImageCropRotateUiState(
            isDownloading = !initialFile.isAvailableLocally,
            localFilePath = initialFile.storagePath.takeIf { initialFile.isAvailableLocally },
        )
    )
    val uiState: StateFlow<ImageCropRotateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prepareInput()
        }
    }

    fun isInputValid(): Boolean {
        val localFile = localFileOrNull() ?: return false
        return localFile.exists() && localFile.canRead()
    }

    fun getInputUri(context: Context): Uri? {
        val localFile = localFileOrNull() ?: return null
        if (!localFile.exists() || !localFile.canRead()) return null
        return uriForFile(context, localFile)
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

    fun cancelDownloadIfNeeded() {
        if (!_uiState.value.isDownloading) return
        viewModelScope.launch {
            withContext(NonCancellable + coroutinesDispatcherProvider.io) {
                cancelDownloadForFileUseCase(CancelDownloadForFileUseCase.Params(ocFile))
            }
        }
    }

    private suspend fun prepareInput() {
        ocFile.id?.let { fileId ->
            withContext(coroutinesDispatcherProvider.io) {
                getFileByIdUseCase(GetFileByIdUseCase.Params(fileId)).getDataOrNull()
            }?.let { ocFile = it }
        }

        if (ocFile.isAvailableLocally) {
            if (isInputValid()) {
                _uiState.update {
                    it.copy(isDownloading = false, localFilePath = ocFile.storagePath, errorMessageRes = null)
                }
            } else {
                Timber.w("Cannot edit image, invalid local path: %s", ocFile.storagePath)
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        localFilePath = null,
                        errorMessageRes = R.string.homecloud_imageedit_load_error,
                    )
                }
            }
            return
        }

        val workId = withContext(coroutinesDispatcherProvider.io) {
            resolveDownloadWorkId(ocFile)
        }
        if (workId == null) {
            Timber.w("Cannot download image for crop: %s", ocFile.remotePath)
            showDownloadError()
            return
        }

        _uiState.update { it.copy(isDownloading = true, errorMessageRes = null) }
        observeDownload(workId)
    }

    private fun resolveDownloadWorkId(file: OCFile): UUID? {
        downloadFileUseCase(DownloadFileUseCase.Params(accountName = file.owner, file = file))?.let { return it }
        return workManager.getWorkInfoByTags(getTagsForDownload(file, file.owner))
            .firstOrNull { !it.state.isFinished }
            ?.id
    }

    private suspend fun observeDownload(workId: UUID) {
        val finished = workManager.getWorkInfoByIdLiveData(workId)
            .asFlow()
            .filterNotNull()
            .onEach { workInfo ->
                if (workInfo.state == WorkInfo.State.RUNNING) {
                    val progress = workInfo.progress.getInt(WORKER_KEY_PROGRESS, INDETERMINATE_DOWNLOAD_PROGRESS)
                    _uiState.update {
                        it.copy(isDownloading = true, downloadProgress = progress)
                    }
                }
            }
            .first { it.state.isFinished }

        when (finished.state) {
            WorkInfo.State.SUCCEEDED -> onDownloadSucceeded()
            WorkInfo.State.CANCELLED -> _uiState.update { it.copy(isDownloading = false) }
            else -> {
                Timber.w("Image download did not succeed: %s", finished.state)
                showDownloadError()
            }
        }
    }

    private suspend fun onDownloadSucceeded() {
        val fileId = ocFile.id
        val refreshed = if (fileId != null) {
            withContext(coroutinesDispatcherProvider.io) {
                getFileByIdUseCase(GetFileByIdUseCase.Params(fileId)).getDataOrNull()
            }
        } else {
            null
        }

        if (refreshed == null || !refreshed.isAvailableLocally) {
            Timber.w("Download succeeded but file is not available locally: %s", ocFile.remotePath)
            showDownloadError()
            return
        }

        ocFile = refreshed
        _uiState.update {
            it.copy(
                isDownloading = false,
                downloadProgress = INDETERMINATE_DOWNLOAD_PROGRESS,
                localFilePath = refreshed.storagePath,
                errorMessageRes = null,
            )
        }
    }

    private fun showDownloadError() {
        _uiState.update {
            it.copy(
                isDownloading = false,
                downloadProgress = INDETERMINATE_DOWNLOAD_PROGRESS,
                errorMessageRes = R.string.homecloud_imageedit_download_error,
            )
        }
    }

    private fun localFileOrNull(): File? =
        ocFile.storagePath?.takeIf { it.isNotBlank() }?.let(::File)

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
