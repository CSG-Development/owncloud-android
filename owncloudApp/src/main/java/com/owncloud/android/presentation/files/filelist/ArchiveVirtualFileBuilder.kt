package com.owncloud.android.presentation.files.filelist

import androidx.work.WorkInfo
import com.owncloud.android.domain.archive.ArchiveMimeTypes
import com.owncloud.android.domain.files.model.MIME_DIR_UNIX
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.model.VirtualArchiveFileIds
import com.owncloud.android.presentation.files.operations.ArchiveWorkEnqueued
import com.owncloud.android.usecases.archive.ARCHIVE_TAG_UNZIP
import com.owncloud.android.usecases.archive.ARCHIVE_TAG_ZIP
import com.owncloud.android.workers.DownloadFileWorker
import java.util.UUID

object ArchiveVirtualFileBuilder {

    fun buildForFolder(
        currentFolder: OCFile,
        persistedFolderContent: List<OCFileWithSyncInfo>,
        pendingWorks: List<WorkInfo>,
        workMetadata: Map<UUID, ArchiveWorkEnqueued>,
    ): List<OCFileWithSyncInfo> {
        val existingRemotePaths = persistedFolderContent.map { it.file.remotePath }.toSet()
        val currentFolderId = currentFolder.id ?: return emptyList()

        return pendingWorks.mapNotNull { workInfo ->
            val metadata = workMetadata[workInfo.id] ?: return@mapNotNull null
            if (metadata.accountName != currentFolder.owner) return@mapNotNull null
            if (metadata.parentFolderId != currentFolderId) return@mapNotNull null
            if (metadata.remotePath in existingRemotePaths) return@mapNotNull null
            if (!workInfo.tags.contains(if (metadata.isCompress) ARCHIVE_TAG_ZIP else ARCHIVE_TAG_UNZIP)) {
                return@mapNotNull null
            }

            metadata.toVirtualFile(
                parentId = currentFolderId,
                workInfo = workInfo,
            )
        }
    }

    private fun ArchiveWorkEnqueued.toVirtualFile(
        parentId: Long,
        workInfo: WorkInfo,
    ): OCFileWithSyncInfo {
        val progress = workInfo.progress.getInt(DownloadFileWorker.WORKER_KEY_PROGRESS, -1)
        val isIndeterminate = progress < 0
        val mimeType = if (isCompress) {
            ArchiveMimeTypes.ZIP
        } else {
            MIME_DIR_UNIX
        }

        return OCFileWithSyncInfo(
            file = OCFile(
                id = VirtualArchiveFileIds.fileIdForWork(workInfo.id),
                parentId = parentId,
                owner = accountName,
                remotePath = remotePath,
                mimeType = mimeType,
                length = 0L,
                creationTimestamp = null,
                modificationTimestamp = System.currentTimeMillis(),
                etag = null,
                storagePath = null,
                spaceId = spaceId,
            ),
            archiveWorkerUuid = workInfo.id,
            uploadProgress = if (isIndeterminate) null else progress.coerceIn(0, 100),
            isProgressIndeterminate = isIndeterminate,
        )
    }
}

fun List<OCFileWithSyncInfo>.withArchiveVirtualFiles(
    currentFolder: OCFile,
    pendingWorks: List<WorkInfo>,
    workMetadata: Map<UUID, ArchiveWorkEnqueued>,
): List<OCFileWithSyncInfo> =
    this + ArchiveVirtualFileBuilder.buildForFolder(
        currentFolder = currentFolder,
        persistedFolderContent = this,
        pendingWorks = pendingWorks,
        workMetadata = workMetadata,
    )
