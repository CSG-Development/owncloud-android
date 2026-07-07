package com.owncloud.android.domain.archive

import com.owncloud.android.domain.files.model.OCFile

object ArchiveMimeTypes {
    const val ZIP = "application/zip"
    private const val ZIP_EXTENSION = "zip"

    fun isZipFile(file: OCFile): Boolean {
        if (file.isFolder) return false
        return file.mimeType.equals(ZIP, ignoreCase = true) ||
            file.fileName.substringAfterLast('.', "").equals(ZIP_EXTENSION, ignoreCase = true)
    }
}
