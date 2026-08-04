package com.owncloud.android.presentation.files.operations

import androidx.work.WorkInfo
import com.owncloud.android.domain.archive.ArchiveExtractLayout
import com.owncloud.android.domain.archive.ArchiveNameResolver
import com.owncloud.android.domain.archive.ZipArchiveExtractor
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.usecases.archive.ARCHIVE_TAG_UNZIP
import com.owncloud.android.usecases.archive.ARCHIVE_TAG_ZIP
import java.io.File
import java.util.UUID

object ArchiveWorkMetadataResolver {

    fun resolve(
        workInfo: WorkInfo,
        accountName: String,
        getFileById: (Long) -> OCFile?,
    ): ArchiveWorkEnqueued? {
        if (!workInfo.tags.contains(accountName)) return null

        val isCompress = when {
            workInfo.tags.contains(ARCHIVE_TAG_ZIP) -> true
            workInfo.tags.contains(ARCHIVE_TAG_UNZIP) -> false
            else -> return null
        }

        val fileIds = workInfo.tags.mapNotNull { tag -> tag.toLongOrNull() }
        if (fileIds.isEmpty()) return null

        val filesById = fileIds.mapNotNull { id ->
            getFileById(id)?.let { id to it }
        }.toMap()

        return if (isCompress) {
            resolveCompress(workInfo.id, accountName, fileIds, filesById, getFileById)
        } else {
            resolveExtract(workInfo.id, accountName, filesById, getFileById)
        }
    }

    private fun resolveCompress(
        workId: UUID,
        accountName: String,
        taggedIds: List<Long>,
        filesById: Map<Long, OCFile>,
        getFileById: (Long) -> OCFile?,
    ): ArchiveWorkEnqueued? {
        val parentFolderId = taggedIds.firstOrNull { candidateParentId ->
            val others = filesById.filterKeys { it != candidateParentId }.values
            others.isNotEmpty() && others.all { it.parentId == candidateParentId }
        } ?: return null

        val parentFolder = filesById[parentFolderId]
            ?: getFileById(parentFolderId)?.takeIf { it.isFolder }
            ?: return null
        if (!parentFolder.isFolder) return null

        val sourceFiles = filesById.filterKeys { it != parentFolderId }.values.toList()
        if (sourceFiles.isEmpty()) return null

        val displayName = ArchiveNameResolver.resolveArchiveBaseName(sourceFiles)
        return ArchiveWorkEnqueued(
            workId = workId,
            displayName = displayName,
            isCompress = true,
            itemCount = sourceFiles.size,
            parentFolderId = parentFolderId,
            remotePath = ArchiveNameResolver.resolveRemoteZipPath(
                parentFolder = parentFolder,
                archiveFileName = displayName,
            ),
            spaceId = parentFolder.spaceId,
            accountName = accountName,
            sourceFileIds = sourceFiles.mapNotNull { it.id },
        )
    }

    private fun resolveExtract(
        workId: UUID,
        accountName: String,
        filesById: Map<Long, OCFile>,
        getFileById: (Long) -> OCFile?,
    ): ArchiveWorkEnqueued? {
        val zipFile = filesById.values.firstOrNull { !it.isFolder } ?: return null
        val parentFolderId = zipFile.parentId ?: return null
        val parentFolder = filesById.values.firstOrNull { it.isFolder }
            ?: getFileById(parentFolderId)?.takeIf { it.isFolder }
            ?: return null

        val (displayName, remotePath) = resolveExtractWorkMetadata(zipFile)
        return ArchiveWorkEnqueued(
            workId = workId,
            displayName = displayName,
            isCompress = false,
            itemCount = 1,
            parentFolderId = parentFolder.id ?: parentFolderId,
            remotePath = remotePath,
            spaceId = zipFile.spaceId,
            accountName = accountName,
            zipFileId = zipFile.id,
        )
    }

    private fun resolveExtractWorkMetadata(zipFile: OCFile): Pair<String, String> {
        val localZipFile = zipFile.storagePath
            ?.let { File(it) }
            .takeIf { zipFile.isAvailableLocally }

        val layout = localZipFile?.let { zip ->
            runCatching { ZipArchiveExtractor.peekLayout(zip) }.getOrNull()
        }

        return when (layout) {
            is ArchiveExtractLayout.DirectToParent -> {
                val entryName = if (layout.isTopLevelFolder) {
                    layout.topLevelRoot + OCFile.PATH_SEPARATOR
                } else {
                    layout.topLevelRoot
                }
                layout.topLevelRoot to (zipFile.getParentRemotePath() + entryName)
            }

            else -> {
                val extractFolderName = zipFile.fileName
                    .substringBeforeLast('.')
                    .ifBlank { zipFile.fileName }
                extractFolderName to ArchiveNameResolver.resolveExtractSubfolderPath(zipFile)
            }
        }
    }
}
