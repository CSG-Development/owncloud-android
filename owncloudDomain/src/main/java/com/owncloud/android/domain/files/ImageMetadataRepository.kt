package com.owncloud.android.domain.files

import com.owncloud.android.domain.files.model.ImageMetadata

interface ImageMetadataRepository {
    fun readMetadata(localPath: String): ImageMetadata
}
