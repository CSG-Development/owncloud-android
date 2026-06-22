package com.owncloud.android.domain.files.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.files.ImageMetadataRepository
import com.owncloud.android.domain.files.model.ImageMetadata
import timber.log.Timber
import java.io.File

class GetImageMetadataUseCase(
    private val imageMetadataRepository: ImageMetadataRepository,
) : BaseUseCaseWithResult<ImageMetadata, GetImageMetadataUseCase.Params>() {

    override fun run(params: Params): ImageMetadata {
        val path = params.localPath.trim()
        if (path.isBlank() || !File(path).exists()) {
            return ImageMetadata(sections = emptyList())
        }
        Timber.d("Get image metadata for $path")
        return imageMetadataRepository.readMetadata(path)
    }

    data class Params(val localPath: String)
}
