package com.owncloud.android.domain.archive

import com.owncloud.android.domain.exceptions.ArchivePathTraversalException
import com.owncloud.android.domain.exceptions.DuplicateArchiveEntryException
import com.owncloud.android.domain.exceptions.InvalidArchiveException
import com.owncloud.android.domain.exceptions.UnsupportedArchiveFormatException
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

object ZipArchiveExtractor {

    fun extract(zipFile: File, targetDirectory: File) {
        if (!zipFile.exists() || !zipFile.isFile) {
            throw InvalidArchiveException(
                IllegalStateException("Zip file does not exist: ${zipFile.absolutePath}"),
            )
        }

        targetDirectory.mkdirs()
        val usedEntryPaths = mutableSetOf<String>()

        try {
            BufferedInputStream(FileInputStream(zipFile)).use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    var entry: ZipEntry? = zipInputStream.nextEntry
                    while (entry != null) {
                        extractEntry(
                            zipInputStream = zipInputStream,
                            entry = entry,
                            targetDirectory = targetDirectory,
                            usedEntryPaths = usedEntryPaths,
                        )
                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
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
        zipInputStream: ZipInputStream,
        entry: ZipEntry,
        targetDirectory: File,
        usedEntryPaths: MutableSet<String>,
    ) {
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
            return
        }

        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { outputStream ->
            zipInputStream.copyTo(outputStream)
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
