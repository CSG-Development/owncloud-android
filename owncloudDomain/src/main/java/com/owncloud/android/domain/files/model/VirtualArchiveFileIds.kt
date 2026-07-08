package com.owncloud.android.domain.files.model

import java.util.UUID

/**
 * Encodes archive WorkManager ids into negative [OCFile.id] values for virtual folder rows.
 * Uses a range far from upload virtual ids ([VirtualUploadFileIds]).
 */
object VirtualArchiveFileIds {

    private const val BASE = Long.MIN_VALUE + 1_000_000L
    private const val RANGE_MASK = 0x7FFFFFFFL

    fun fileIdForWork(workId: UUID): Long =
        BASE + (workId.hashCode().toLong() and RANGE_MASK)

    fun isArchiveVirtualFileId(fileId: Long): Boolean =
        fileId in BASE..(BASE + RANGE_MASK)
}

fun OCFile.isArchiveVirtualFile(): Boolean =
    id?.let(VirtualArchiveFileIds::isArchiveVirtualFileId) == true

fun OCFile.isVirtualFile(): Boolean = isUploadVirtualFile() || isArchiveVirtualFile()
