package com.owncloud.android.domain.archive

import com.owncloud.android.domain.exceptions.CancelledException
import com.owncloud.android.domain.exceptions.DuplicateArchiveEntryException
import com.owncloud.android.domain.exceptions.InvalidArchiveException
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipArchiveBuilder {

    fun build(
        fileEntries: List<ArchiveEntryWithLocalPath>,
        emptyDirectoryPaths: Set<String>,
        outputZipFile: File,
        onBytesProcessed: ((processed: Long, total: Long) -> Unit)? = null,
        isCancelled: () -> Boolean = { false },
    ) {
        outputZipFile.parentFile?.mkdirs()

        val usedEntryPaths = mutableSetOf<String>()
        val totalBytes = if (onBytesProcessed != null) {
            fileEntries.sumOf { it.localFile.length() }
        } else {
            0L
        }
        var processedBytes = 0L

        FileOutputStream(outputZipFile).use { fileOutputStream ->
            ZipOutputStream(fileOutputStream).use { zipOutputStream ->
                emptyDirectoryPaths.sorted().forEach { directoryPath ->
                    if (isCancelled()) throw CancelledException()
                    val normalizedPath = ensureDirectoryEntryPath(directoryPath)
                    if (usedEntryPaths.add(normalizedPath)) {
                        zipOutputStream.putNextEntry(ZipEntry(normalizedPath))
                        zipOutputStream.closeEntry()
                    }
                }

                fileEntries.sortedBy { it.zipEntryPath }.forEach { entry ->
                    if (isCancelled()) throw CancelledException()
                    val normalizedPath = entry.zipEntryPath.replace('\\', '/').trimStart('/')
                    if (!usedEntryPaths.add(normalizedPath)) {
                        throw DuplicateArchiveEntryException(normalizedPath)
                    }

                    val localFile = entry.localFile
                    if (!localFile.exists() || !localFile.isFile) {
                        throw InvalidArchiveException(
                            IllegalStateException("Missing local file for zip entry: $normalizedPath"),
                        )
                    }

                    zipOutputStream.putNextEntry(ZipEntry(normalizedPath))
                    BufferedInputStream(FileInputStream(localFile)).use { inputStream ->
                        inputStream.copyTo(zipOutputStream)
                    }
                    zipOutputStream.closeEntry()

                    if (onBytesProcessed != null) {
                        processedBytes += localFile.length()
                        onBytesProcessed(processedBytes, totalBytes)
                    }
                }
            }
        }
    }

    private fun ensureDirectoryEntryPath(path: String): String {
        val normalized = path.replace('\\', '/').trimStart('/')
        return if (normalized.endsWith('/')) normalized else "$normalized/"
    }
}
