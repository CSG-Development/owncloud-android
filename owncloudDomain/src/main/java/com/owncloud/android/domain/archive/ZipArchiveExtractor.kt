package com.owncloud.android.domain.archive

import com.owncloud.android.domain.exceptions.ArchivePathTraversalException
import com.owncloud.android.domain.exceptions.DuplicateArchiveEntryException
import com.owncloud.android.domain.exceptions.InvalidArchiveException
import com.owncloud.android.domain.exceptions.UnsupportedArchiveFormatException
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

object ZipArchiveExtractor {

    fun extract(
        zipFile: File,
        targetDirectory: File,
        onBytesProcessed: ((processed: Long, total: Long) -> Unit)? = null,
    ) {
        if (!zipFile.exists() || !zipFile.isFile) {
            throw InvalidArchiveException(
                IllegalStateException("Zip file does not exist: ${zipFile.absolutePath}"),
            )
        }

        targetDirectory.mkdirs()
        val usedEntryPaths = mutableSetOf<String>()
        val totalBytes = if (onBytesProcessed != null) zipFile.length() else 0L
        var processedBytes = 0L

        try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val bytesRead = zip.getInputStream(entry).use { inputStream ->
                        extractEntry(
                            inputStream = inputStream,
                            entry = entry,
                            targetDirectory = targetDirectory,
                            usedEntryPaths = usedEntryPaths,
                        )
                    }
                    if (onBytesProcessed != null) {
                        processedBytes += bytesRead
                        onBytesProcessed(processedBytes.coerceAtMost(totalBytes), totalBytes)
                    }
                }
            }
        } catch (exception: ZipException) {
            throw UnsupportedArchiveFormatException()
        } catch (exception: InvalidArchiveException) {
            throw exception
        } catch (exception: ArchivePathTraversalException) {
            throw exception
        } catch (exception: DuplicateArchiveEntryException) {
            throw exception
        } catch (exception: UnsupportedArchiveFormatException) {
            throw exception
        } catch (exception: Exception) {
            throw InvalidArchiveException(exception)
        }
    }

    private fun extractEntry(
        inputStream: InputStream,
        entry: ZipEntry,
        targetDirectory: File,
        usedEntryPaths: MutableSet<String>,
    ): Long {
        val sanitizedPath = sanitizeEntryPath(entry.name)
        if (!usedEntryPaths.add(sanitizedPath)) {
            throw DuplicateArchiveEntryException(sanitizedPath)
        }

        val outputFile = File(targetDirectory, sanitizedPath)
        val canonicalTargetDirectory = targetDirectory.canonicalFile
        val canonicalOutputFile = outputFile.canonicalFile
        if (!canonicalOutputFile.path.startsWith(canonicalTargetDirectory.path + File.separator) &&
            canonicalOutputFile != canonicalTargetDirectory
        ) {
            throw ArchivePathTraversalException(entry.name)
        }

        if (entry.isDirectory || sanitizedPath.endsWith('/')) {
            outputFile.mkdirs()
            Timber.d("Extracted ${entry.name}: 0 bytes (directory)")
            return 0L
        }

        outputFile.parentFile?.mkdirs()
        val bytesRead = FileOutputStream(outputFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        Timber.d("Extracted ${entry.name}: $bytesRead bytes (method=${entry.method})")
        return bytesRead
    }

    internal fun sanitizeEntryPath(entryName: String): String {
        val normalized = entryName.replace('\\', '/')
        val segments = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        segments.forEach { segment ->
            if (segment == "..") {
                throw ArchivePathTraversalException(entryName)
            }
        }
        return segments.joinToString("/")
    }
}
