package com.owncloud.android.domain.archive

import com.owncloud.android.domain.exceptions.ArchivePathTraversalException
import com.owncloud.android.domain.exceptions.CancelledException
import com.owncloud.android.domain.exceptions.DuplicateArchiveEntryException
import com.owncloud.android.domain.exceptions.InvalidArchiveException
import com.owncloud.android.domain.exceptions.PasswordProtectedArchiveException
import com.owncloud.android.domain.exceptions.UnsupportedArchiveFormatException
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

sealed class ArchiveExtractLayout {

    data class DirectToParent(
        val topLevelRoot: String,
        val isTopLevelFolder: Boolean,
    ) : ArchiveExtractLayout()

    data object IntoArchiveFolder : ArchiveExtractLayout()
}

object ZipArchiveExtractor {

    fun peekLayout(zipFile: File): ArchiveExtractLayout {
        validateZipFile(zipFile)
        return runZipOperation(zipFile) { zip ->
            resolveLayout(collectEntryInfos(zip))
        }
    }

    fun extract(
        zipFile: File,
        targetDirectory: File,
        onBytesProcessed: ((processed: Long, total: Long) -> Unit)? = null,
        isCancelled: () -> Boolean = { false },
    ): ArchiveExtractLayout {
        validateZipFile(zipFile)
        targetDirectory.mkdirs()

        val usedEntryPaths = mutableSetOf<String>()
        val entryInfos = mutableListOf<Pair<String, Boolean>>()
        val totalBytes = if (onBytesProcessed != null) zipFile.length() else 0L
        var processedBytes = 0L

        return runZipOperation(zipFile) { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                if (isCancelled()) throw CancelledException()
                val entry = entries.nextElement()
                val sanitizedPath = sanitizeEntryPath(entry.name)
                if (sanitizedPath.isNotEmpty()) {
                    entryInfos.add(
                        sanitizedPath to (entry.isDirectory || sanitizedPath.endsWith('/')),
                    )
                }
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
            resolveLayout(entryInfos)
        }
    }

    private fun collectEntryInfos(zip: ZipFile): List<Pair<String, Boolean>> {
        val entryInfos = mutableListOf<Pair<String, Boolean>>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val sanitizedPath = sanitizeEntryPath(entry.name)
            if (sanitizedPath.isEmpty()) continue
            entryInfos.add(
                sanitizedPath to (entry.isDirectory || sanitizedPath.endsWith('/')),
            )
        }
        return entryInfos
    }

    private fun resolveLayout(entryInfos: List<Pair<String, Boolean>>): ArchiveExtractLayout {
        if (entryInfos.isEmpty()) {
            throw InvalidArchiveException(
                IllegalStateException("Zip file contains no entries"),
            )
        }

        val topLevelRoots = entryInfos
            .map { (path, _) -> path.substringBefore('/') }
            .toSet()

        return if (topLevelRoots.size == 1) {
            val root = topLevelRoots.first()
            val isTopLevelFolder = entryInfos.any { (path, isDirectory) ->
                path.contains('/') || isDirectory
            }
            ArchiveExtractLayout.DirectToParent(
                topLevelRoot = root,
                isTopLevelFolder = isTopLevelFolder,
            )
        } else {
            ArchiveExtractLayout.IntoArchiveFolder
        }
    }

    private fun <T> runZipOperation(zipFile: File, block: (ZipFile) -> T): T {
        try {
            return ZipFile(zipFile).use(block)
        } catch (exception: ZipException) {
            if (ZipEncryptionDetector.containsEncryptedEntries(zipFile)) {
                throw PasswordProtectedArchiveException()
            }
            throw UnsupportedArchiveFormatException()
        } catch (exception: InvalidArchiveException) {
            throw exception
        } catch (exception: ArchivePathTraversalException) {
            throw exception
        } catch (exception: DuplicateArchiveEntryException) {
            throw exception
        } catch (exception: UnsupportedArchiveFormatException) {
            throw exception
        } catch (exception: CancelledException) {
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

    private fun validateZipFile(zipFile: File) {
        if (!zipFile.exists() || !zipFile.isFile) {
            throw InvalidArchiveException(
                IllegalStateException("Zip file does not exist: ${zipFile.absolutePath}"),
            )
        }
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
