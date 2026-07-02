package com.owncloud.android.data.trash.datasources.implementation

import com.owncloud.android.data.ClientManager
import com.owncloud.android.data.executeRemoteOperation
import com.owncloud.android.data.trash.datasources.RemoteTrashDataSource
import com.owncloud.android.domain.trash.model.HCTrashItem
import com.owncloud.android.lib.resources.trash.RemoteTrashItem

class HCRemoteTrashDataSource(
    private val clientManager: ClientManager,
) : RemoteTrashDataSource {

    override fun listTrash(accountName: String): List<HCTrashItem> =
        executeRemoteOperation {
            clientManager.getTrashService(accountName).listTrash()
        }.map { it.toModel() }

    override fun restoreTrashItem(
        accountName: String,
        fileId: String,
        originalLocation: String,
        forceOverride: Boolean,
    ) {
        executeRemoteOperation {
            clientManager.getTrashService(accountName).restoreItem(
                fileId = fileId,
                originalLocation = originalLocation,
                forceOverride = forceOverride,
            )
        }
    }

    override fun deleteTrashItemPermanently(accountName: String, fileId: String) {
        executeRemoteOperation {
            clientManager.getTrashService(accountName).deleteItemPermanently(fileId)
        }
    }

    companion object {
        fun RemoteTrashItem.toModel(): HCTrashItem =
            HCTrashItem(
                fileId = fileId,
                trashDavPath = trashDavPath,
                originalFilename = originalFilename,
                originalLocation = originalLocation,
                deletedAt = deletedAt,
                deletedTimestamp = deletedTimestamp,
                contentLength = contentLength,
                mimeType = mimeType,
                lastModified = lastModified,
            )
    }
}
