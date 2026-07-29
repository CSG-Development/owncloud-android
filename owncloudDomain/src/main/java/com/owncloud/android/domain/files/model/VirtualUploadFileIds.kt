package com.owncloud.android.domain.files.model

/**
 * Encodes upload transfer ids into negative [OCFile.id] values for virtual folder rows.
 * Negative ids cannot collide with persisted file ids (always positive).
 */
object VirtualUploadFileIds {

    fun fileIdForTransfer(transferId: Long): Long = -transferId - 1L

    fun transferIdFromFileId(fileId: Long?): Long? =
        fileId?.takeIf(::isUploadVirtualFileId)?.let { -it - 1L }

    fun isUploadVirtualFileId(fileId: Long): Boolean = fileId < 0L
}

fun OCFile.isUploadVirtualFile(): Boolean = id?.let(VirtualUploadFileIds::isUploadVirtualFileId) == true

fun OCFile.uploadTransferId(): Long? = VirtualUploadFileIds.transferIdFromFileId(id)

fun OCFile.isVirtualFile(): Boolean = isUploadVirtualFile()
