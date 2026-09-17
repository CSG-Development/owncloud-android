package com.owncloud.android.presentation.imageedit

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.usecases.GetFileByIdUseCase
import com.owncloud.android.domain.files.usecases.GetFileByRemotePathUseCase
import com.owncloud.android.extensions.getTagsForDownload
import com.owncloud.android.extensions.getWorkInfoByTags
import com.owncloud.android.providers.ContextProvider
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import com.owncloud.android.usecases.transfers.downloads.CancelDownloadForFileUseCase
import com.owncloud.android.usecases.transfers.downloads.DownloadFileUseCase
import com.owncloud.android.usecases.transfers.uploads.UploadFileInConflictUseCase
import com.owncloud.android.usecases.transfers.uploads.UploadFilesFromSystemUseCase
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

sealed interface ImageCropRotateUiState {
    val errorMessageRes: Int? get() = null

    data class Downloading(
        val progress: Int = INDETERMINATE_DOWNLOAD_PROGRESS,
    ) : ImageCropRotateUiState

    data class LoadingImage(
        val localFilePath: String,
        override val errorMessageRes: Int? = null,
    ) : ImageCropRotateUiState

    data class Ready(
        val localFilePath: String,
        override val errorMessageRes: Int? = null,
    ) : ImageCropRotateUiState

    data class Saving(val localFilePath: String) : ImageCropRotateUiState

    data class NameConflict(
        val localFilePath: String,
        val existingFileName: String,
        val tempOutputFile: File,
    ) : ImageCropRotateUiState

    data class Saved(val outputFile: File) : ImageCropRotateUiState

    data class Unavailable(
        override val errorMessageRes: Int? = null,
    ) : ImageCropRotateUiState
}

fun ImageCropRotateUiState.localFilePath(): String? = when (this) {
    is ImageCropRotateUiState.LoadingImage -> localFilePath
    is ImageCropRotateUiState.Ready -> localFilePath
    is ImageCropRotateUiState.Saving -> localFilePath
    is ImageCropRotateUiState.NameConflict -> localFilePath
    is ImageCropRotateUiState.Downloading,
    is ImageCropRotateUiState.Saved,
    is ImageCropRotateUiState.Unavailable -> null
}

class ImageCropRotateViewModel(
    private val contextProvider: ContextProvider,
    private val getFileByIdUseCase: GetFileByIdUseCase,
    private val getFileByRemotePathUseCase: GetFileByRemotePathUseCase,
    private val downloadFileUseCase: DownloadFileUseCase,
    private val cancelDownloadForFileUseCase: CancelDownloadForFileUseCase,
    private val uploadFileInConflictUseCase: UploadFileInConflictUseCase,
    private val uploadFilesFromSystemUseCase: UploadFilesFromSystemUseCase,
    private val workManager: WorkManager,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    initialFile: OCFile,
) : ViewModel() {

    private var ocFile: OCFile = initialFile

    private val _uiState = MutableStateFlow(
        if (initialFile.isAvailableLocally) {
            ImageCropRotateUiState.LoadingImage(localFilePath = initialFile.storagePath.orEmpty())
        } else {
            ImageCropRotateUiState.Downloading()
        }
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

    fun getOutputCompressFormat(): Bitmap.CompressFormat = resolveOutputFormat().compressFormat

    fun createOutputFile(): File {
        val dir = File(contextProvider.getContext().cacheDir, OUTPUT_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val extension = resolveOutputFormat().extension
        return File(dir, "${UUID.randomUUID()}.$extension").also { file ->
            file.createNewFile()
        }
    }

    fun onImageLoaded(success: Boolean) {
        _uiState.update { current ->
            when (current) {
                is ImageCropRotateUiState.LoadingImage,
                is ImageCropRotateUiState.Ready -> {
                    val path = current.localFilePath() ?: return@update current
                    if (success) {
                        ImageCropRotateUiState.Ready(localFilePath = path)
                    } else {
                        ImageCropRotateUiState.LoadingImage(
                            localFilePath = path,
                            errorMessageRes = R.string.homecloud_imageedit_load_error,
                        )
                    }
                }
                else -> current
            }
        }
    }

    fun onSaveStarted() {
        _uiState.update { current ->
            val path = current.localFilePath() ?: return@update current
            ImageCropRotateUiState.Saving(localFilePath = path)
        }
    }

    fun onSaveCompleted(outputFile: File?, error: Exception?) {
        if (error != null) {
            Timber.e(error, "Failed to crop and rotate image")
        }
        val savedSuccessfully = error == null && outputFile != null && outputFile.exists() && outputFile.length() > 0
        if (!savedSuccessfully || outputFile == null) {
            showSaveError()
            return
        }
        viewModelScope.launch {
            persistOrShowConflict(outputFile)
        }
    }

    fun onOverwriteChosen() {
        val conflict = _uiState.value as? ImageCropRotateUiState.NameConflict ?: return
        enqueueChosenUpload(tempOutputFile = conflict.tempOutputFile, overwrite = true)
    }

    fun onSaveAsCopyChosen() {
        val conflict = _uiState.value as? ImageCropRotateUiState.NameConflict ?: return
        enqueueChosenUpload(tempOutputFile = conflict.tempOutputFile, overwrite = false)
    }

    fun onConflictDismissed() {
        _uiState.update { current ->
            if (current !is ImageCropRotateUiState.NameConflict) return@update current
            current.tempOutputFile.delete()
            ImageCropRotateUiState.Ready(localFilePath = current.localFilePath)
        }
    }

    fun consumeError() {
        _uiState.update { current ->
            when (current) {
                is ImageCropRotateUiState.LoadingImage -> current.copy(errorMessageRes = null)
                is ImageCropRotateUiState.Ready -> current.copy(errorMessageRes = null)
                is ImageCropRotateUiState.Unavailable -> current.copy(errorMessageRes = null)
                is ImageCropRotateUiState.Downloading,
                is ImageCropRotateUiState.Saving,
                is ImageCropRotateUiState.NameConflict,
                is ImageCropRotateUiState.Saved -> current
            }
        }
    }

    fun cancelDownloadIfNeeded() {
        if (_uiState.value !is ImageCropRotateUiState.Downloading) return
        viewModelScope.launch {
            withContext(NonCancellable + coroutinesDispatcherProvider.io) {
                cancelDownloadForFileUseCase(CancelDownloadForFileUseCase.Params(ocFile))
            }
        }
    }

    private suspend fun persistOrShowConflict(outputFile: File) {
        val path = _uiState.value.localFilePath() ?: return
        val targetName = targetFileName()
        val existing = withContext(coroutinesDispatcherProvider.io) {
            getFileByRemotePathUseCase(
                GetFileByRemotePathUseCase.Params(
                    owner = ocFile.owner,
                    remotePath = ocFile.getParentRemotePath() + targetName,
                    spaceId = ocFile.spaceId,
                )
            ).getDataOrNull()
        }
        if (existing != null) {
            _uiState.update {
                ImageCropRotateUiState.NameConflict(
                    localFilePath = path,
                    existingFileName = targetName,
                    tempOutputFile = outputFile,
                )
            }
            return
        }
        enqueueUploadAndFinish(tempOutputFile = outputFile, overwrite = false)
    }

    private fun enqueueChosenUpload(tempOutputFile: File, overwrite: Boolean) {
        val path = _uiState.value.localFilePath() ?: return
        _uiState.update { ImageCropRotateUiState.Saving(localFilePath = path) }
        viewModelScope.launch {
            enqueueUploadAndFinish(tempOutputFile = tempOutputFile, overwrite = overwrite)
        }
    }

    private suspend fun enqueueUploadAndFinish(tempOutputFile: File, overwrite: Boolean) {
        val path = _uiState.value.localFilePath() ?: return
        runCatching {
            withContext(coroutinesDispatcherProvider.io) {
                enqueueUpload(tempOutputFile = tempOutputFile, overwrite = overwrite)
            }
        }.onSuccess {
            _uiState.update { ImageCropRotateUiState.Saved(outputFile = tempOutputFile) }
        }.onFailure { throwable ->
            Timber.e(throwable, "Failed to enqueue edited image upload")
            _uiState.update {
                ImageCropRotateUiState.Ready(
                    localFilePath = path,
                    errorMessageRes = R.string.homecloud_imageedit_save_error,
                )
            }
        }
    }

    private fun enqueueUpload(tempOutputFile: File, overwrite: Boolean) {
        val folderPath = ocFile.getParentRemotePath()
        val targetName = targetFileName()
        if (overwrite) {
            val namedFile = File(tempOutputFile.parent, targetName)
            val fileToUpload = if (namedFile.absolutePath == tempOutputFile.absolutePath) {
                tempOutputFile
            } else {
                tempOutputFile.copyTo(namedFile, overwrite = true)
            }
            uploadFileInConflictUseCase(
                UploadFileInConflictUseCase.Params(
                    accountName = ocFile.owner,
                    localPath = fileToUpload.absolutePath,
                    uploadFolderPath = folderPath,
                    spaceId = ocFile.spaceId,
                )
            ) ?: error("Could not enqueue overwrite upload")
        } else {
            uploadFilesFromSystemUseCase(
                UploadFilesFromSystemUseCase.Params(
                    accountName = ocFile.owner,
                    listOfLocalPaths = listOf(tempOutputFile.absolutePath),
                    uploadFolderPath = folderPath,
                    listOfRemoteNames = listOf(targetName),
                    spaceId = ocFile.spaceId,
                )
            )
        }
    }

    private fun showSaveError() {
        _uiState.update { current ->
            val path = current.localFilePath() ?: return@update current
            ImageCropRotateUiState.Ready(
                localFilePath = path,
                errorMessageRes = R.string.homecloud_imageedit_save_error,
            )
        }
    }

    private suspend fun prepareInput() {
        ocFile.id?.let { fileId ->
            withContext(coroutinesDispatcherProvider.io) {
                getFileByIdUseCase(GetFileByIdUseCase.Params(fileId)).getDataOrNull()
            }?.let { ocFile = it }
        }

        if (ocFile.isAvailableLocally) {
            val localPath = ocFile.storagePath
            if (isInputValid() && !localPath.isNullOrBlank()) {
                _uiState.update { ImageCropRotateUiState.LoadingImage(localFilePath = localPath) }
            } else {
                Timber.w("Cannot edit image, invalid local path: %s", ocFile.storagePath)
                _uiState.update {
                    ImageCropRotateUiState.Unavailable(errorMessageRes = R.string.homecloud_imageedit_load_error)
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

        _uiState.update { ImageCropRotateUiState.Downloading() }
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
                    _uiState.update { ImageCropRotateUiState.Downloading(progress = progress) }
                }
            }
            .first { it.state.isFinished }

        when (finished.state) {
            WorkInfo.State.SUCCEEDED -> onDownloadSucceeded()
            WorkInfo.State.CANCELLED -> _uiState.update { ImageCropRotateUiState.Unavailable() }
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
        val localPath = refreshed.storagePath
        if (localPath.isNullOrBlank()) {
            showDownloadError()
            return
        }
        _uiState.update { ImageCropRotateUiState.LoadingImage(localFilePath = localPath) }
    }

    private fun showDownloadError() {
        _uiState.update {
            ImageCropRotateUiState.Unavailable(errorMessageRes = R.string.homecloud_imageedit_download_error)
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

    private fun targetFileName(): String {
        val outputFormat = resolveOutputFormat()
        val originalName = ocFile.fileName
        val originalExtension = originalName.substringAfterLast('.', missingDelimiterValue = "")
        if (outputFormat.matchesExtension(originalExtension)) {
            return originalName
        }
        val baseName = originalName.substringBeforeLast('.', originalName)
        return "$baseName.${outputFormat.extension}"
    }

    private fun resolveOutputFormat(): OutputFormat {
        val mimeType = ocFile.mimeType.takeIf { it.startsWith(MIME_PREFIX_IMAGE) }
            ?: ocFile.getMimeTypeFromName().orEmpty()
        return when (mimeType.lowercase()) {
            MIME_PNG -> OutputFormat(Bitmap.CompressFormat.PNG, EXTENSION_PNG)
            MIME_WEBP -> OutputFormat(webpCompressFormat(), EXTENSION_WEBP)
            MIME_JPEG, MIME_JPG -> OutputFormat(Bitmap.CompressFormat.JPEG, EXTENSION_JPG)
            else -> OutputFormat(Bitmap.CompressFormat.JPEG, EXTENSION_JPG)
        }
    }

    private fun webpCompressFormat(): Bitmap.CompressFormat {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }

    private data class OutputFormat(
        val compressFormat: Bitmap.CompressFormat,
        val extension: String,
    ) {
        fun matchesExtension(originalExtension: String): Boolean {
            if (extension.equals(originalExtension, ignoreCase = true)) return true
            return extension == EXTENSION_JPG && originalExtension.equals(EXTENSION_JPEG, ignoreCase = true)
        }
    }

    companion object {
        const val OUTPUT_DIR = "image_edit"
        private const val MIME_PREFIX_IMAGE = "image/"
        private const val MIME_PNG = "image/png"
        private const val MIME_WEBP = "image/webp"
        private const val MIME_JPEG = "image/jpeg"
        private const val MIME_JPG = "image/jpg"
        private const val EXTENSION_PNG = "png"
        private const val EXTENSION_WEBP = "webp"
        private const val EXTENSION_JPG = "jpg"
        private const val EXTENSION_JPEG = "jpeg"
    }
}
