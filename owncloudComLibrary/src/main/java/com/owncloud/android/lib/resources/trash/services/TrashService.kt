package com.owncloud.android.lib.resources.trash.services

import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.resources.trash.RemoteTrashItem

interface TrashService {

    fun listTrash(): RemoteOperationResult<List<RemoteTrashItem>>

    fun restoreItem(fileId: String, originalLocation: String, forceOverride: Boolean): RemoteOperationResult<Unit>

    fun deleteItemPermanently(fileId: String): RemoteOperationResult<Unit>
}
