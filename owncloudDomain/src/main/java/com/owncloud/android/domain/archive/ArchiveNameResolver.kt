package com.owncloud.android.domain.archive

import com.owncloud.android.domain.files.model.OCFile

object ArchiveNameResolver {

    private const val DEFAULT_MULTI_SELECT_NAME = "Archive"
    private const val ZIP_EXTENSION = ".zip"

    fun resolveArchiveBaseName(selectedFiles: List<OCFile>, parentFolder: OCFile): String {
        require(selectedFiles.isNotEmpty()) { "At least one file must be selected" }

        val baseName = if (selectedFiles.size == 1) {
            val fileName = selectedFiles.first().fileName
            fileName.substringBeforeLast('.').ifBlank { fileName }
        } else {
            parentFolder.fileName.takeUnless { it == OCFile.ROOT_PATH } ?: DEFAULT_MULTI_SELECT_NAME
        }

        return if (baseName.endsWith(ZIP_EXTENSION, ignoreCase = true)) {
            baseName
        } else {
            "$baseName$ZIP_EXTENSION"
        }
    }

    fun resolveRemoteZipPath(parentFolder: OCFile, archiveFileName: String): String {
        val parentPath = parentFolder.remotePath.let { path ->
            if (path.endsWith(OCFile.PATH_SEPARATOR)) path else "$path${OCFile.PATH_SEPARATOR}"
        }
        return parentPath + archiveFileName
    }

    fun resolveExtractSubfolderPath(zipFile: OCFile): String {
        val folderName = zipFile.fileName
            .substringBeforeLast('.')
            .ifBlank { zipFile.fileName }
        return zipFile.getParentRemotePath() + folderName + OCFile.PATH_SEPARATOR
    }
}
