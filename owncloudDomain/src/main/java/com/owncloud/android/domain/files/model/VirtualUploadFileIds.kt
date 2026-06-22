package com.owncloud.android.domain.files.model

/**
 * Encodes upload transfer ids into negative [OCFile.id] values for virtual folder rows.
 * Negative ids cannot collide with persisted file ids (always positive).
 */
object VirtualUploadFileIds {

    fun fileIdForTransfer(transferId: Long): Long = -transferId - 1L

    fun transferIdFromFileId(fileId: Long?): Long? =
        fileId?.takeIf(::isVirtualFileId)?.let { -it - 1L }

    fun isVirtualFileId(fileId: Long): Boolean = fileId < 0L
}

fun OCFile.isUploadVirtualFile(): Boolean = id?.let(VirtualUploadFileIds::isVirtualFileId) == true

fun OCFile.uploadTransferId(): Long? = VirtualUploadFileIds.transferIdFromFileId(id)
