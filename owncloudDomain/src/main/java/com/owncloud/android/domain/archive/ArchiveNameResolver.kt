package com.owncloud.android.domain.archive

import com.owncloud.android.domain.files.model.OCFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ArchiveNameResolver {

    private const val MULTI_SELECT_NAME_PREFIX = "Archive_"
    private const val ZIP_EXTENSION = ".zip"
    private const val DATE_PATTERN = "yyyy-MM-dd"

    fun resolveArchiveBaseName(selectedFiles: List<OCFile>): String {
        require(selectedFiles.isNotEmpty()) { "At least one file must be selected" }

        val baseName = if (selectedFiles.size == 1) {
            val fileName = selectedFiles.first().fileName
            fileName.substringBeforeLast('.').ifBlank { fileName }
        } else {
            MULTI_SELECT_NAME_PREFIX + formatCurrentDate()
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

    private fun formatCurrentDate(): String =
        SimpleDateFormat(DATE_PATTERN, Locale.US).format(Date())
}
