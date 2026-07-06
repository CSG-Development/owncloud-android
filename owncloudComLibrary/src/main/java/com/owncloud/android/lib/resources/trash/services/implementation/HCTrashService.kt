package com.owncloud.android.lib.resources.trash.services.implementation

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.resources.trash.DeleteRemoteTrashItemOperation
import com.owncloud.android.lib.resources.trash.ReadRemoteTrashOperation
import com.owncloud.android.lib.resources.trash.RemoteTrashItem
import com.owncloud.android.lib.resources.trash.RestoreRemoteTrashItemOperation
import com.owncloud.android.lib.resources.trash.services.TrashService

class HCTrashService(private val client: OwnCloudClient) : TrashService {
    override fun listTrash(): RemoteOperationResult<List<RemoteTrashItem>> =
        ReadRemoteTrashOperation().execute(client)

    override fun restoreItem(
        fileId: String,
        originalLocation: String,
        forceOverride: Boolean,
    ): RemoteOperationResult<Unit> =
        RestoreRemoteTrashItemOperation(
            fileId = fileId,
            originalLocation = originalLocation,
            forceOverride = forceOverride,
        ).execute(client)

    override fun deleteItemPermanently(fileId: String): RemoteOperationResult<Unit> =
        DeleteRemoteTrashItemOperation(fileId).execute(client)
}
