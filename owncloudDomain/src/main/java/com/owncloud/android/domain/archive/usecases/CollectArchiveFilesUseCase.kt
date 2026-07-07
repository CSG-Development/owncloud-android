package com.owncloud.android.domain.archive.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.archive.ArchiveCollectionResult
import com.owncloud.android.domain.archive.ArchiveEntry
import com.owncloud.android.domain.exceptions.DuplicateArchiveEntryException
import com.owncloud.android.domain.files.FileRepository
import com.owncloud.android.domain.files.model.OCFile

class CollectArchiveFilesUseCase(
    private val fileRepository: FileRepository,
) : BaseUseCaseWithResult<ArchiveCollectionResult, CollectArchiveFilesUseCase.Params>() {

    override fun run(params: Params): ArchiveCollectionResult {
        val fileEntries = mutableListOf<ArchiveEntry>()
        val emptyDirectoryPaths = mutableSetOf<String>()
        val usedEntryPaths = mutableSetOf<String>()

        params.selectedFiles.forEach { selectedFile ->
            if (selectedFile.isFolder) {
                collectFolder(
                    folder = selectedFile,
                    rootEntryName = selectedFile.fileName,
                    accountName = params.accountName,
                    fileEntries = fileEntries,
                    emptyDirectoryPaths = emptyDirectoryPaths,
                    usedEntryPaths = usedEntryPaths,
                )
            } else {
                addFileEntry(
                    ocFile = selectedFile,
                    zipEntryPath = selectedFile.fileName,
                    fileEntries = fileEntries,
                    usedEntryPaths = usedEntryPaths,
                )
            }
        }

        return ArchiveCollectionResult(
            fileEntries = fileEntries,
            emptyDirectoryPaths = emptyDirectoryPaths,
        )
    }

    private fun collectFolder(
        folder: OCFile,
        rootEntryName: String,
        accountName: String,
        fileEntries: MutableList<ArchiveEntry>,
        emptyDirectoryPaths: MutableSet<String>,
        usedEntryPaths: MutableSet<String>,
    ) {
        val folderContents = fileRepository.refreshFolder(
            remotePath = folder.remotePath,
            accountName = accountName,
            spaceId = folder.spaceId,
        ).drop(1)

        if (folderContents.isEmpty()) {
            addDirectoryPath("$rootEntryName/", emptyDirectoryPaths, usedEntryPaths)
            return
        }

        folderContents.forEach { child ->
            if (child.isFolder) {
                collectFolder(
                    folder = child,
                    rootEntryName = "$rootEntryName/${child.fileName}",
                    accountName = accountName,
                    fileEntries = fileEntries,
                    emptyDirectoryPaths = emptyDirectoryPaths,
                    usedEntryPaths = usedEntryPaths,
                )
            } else {
                addFileEntry(
                    ocFile = child,
                    zipEntryPath = "$rootEntryName/${child.fileName}",
                    fileEntries = fileEntries,
                    usedEntryPaths = usedEntryPaths,
                )
            }
        }
    }

    private fun addFileEntry(
        ocFile: OCFile,
        zipEntryPath: String,
        fileEntries: MutableList<ArchiveEntry>,
        usedEntryPaths: MutableSet<String>,
    ) {
        val normalizedPath = normalizeEntryPath(zipEntryPath)
        if (!usedEntryPaths.add(normalizedPath)) {
            throw DuplicateArchiveEntryException(normalizedPath)
        }
        fileEntries.add(ArchiveEntry(ocFile = ocFile, zipEntryPath = normalizedPath))
    }

    private fun addDirectoryPath(
        directoryPath: String,
        emptyDirectoryPaths: MutableSet<String>,
        usedEntryPaths: MutableSet<String>,
    ) {
        val normalizedPath = normalizeEntryPath(directoryPath)
        usedEntryPaths.add(normalizedPath)
        emptyDirectoryPaths.add(normalizedPath)
    }

    private fun normalizeEntryPath(path: String): String =
        path.replace('\\', '/').trimStart('/')

    data class Params(
        val selectedFiles: List<OCFile>,
        val accountName: String,
    )
}
