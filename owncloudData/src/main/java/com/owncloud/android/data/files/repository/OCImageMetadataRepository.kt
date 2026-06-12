package com.owncloud.android.data.files.repository

import com.drew.imaging.ImageMetadataReader
import com.owncloud.android.data.files.metadata.ImageMetadataMapper
import com.owncloud.android.domain.files.ImageMetadataRepository
import com.owncloud.android.domain.files.model.ImageMetadata
import timber.log.Timber
import java.io.File

class OCImageMetadataRepository : ImageMetadataRepository {

    override fun readMetadata(localPath: String): ImageMetadata {
        return try {
            val metadata = ImageMetadataReader.readMetadata(File(localPath))
            ImageMetadataMapper.map(metadata)
        } catch (exception: Exception) {
            Timber.w(exception, "Failed to read image metadata from %s", localPath)
            ImageMetadata(sections = emptyList())
        }
    }
}
