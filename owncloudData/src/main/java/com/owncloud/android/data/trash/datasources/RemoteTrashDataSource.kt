package com.owncloud.android.data.trash.datasources

import com.owncloud.android.domain.trash.model.HCTrashItem

interface RemoteTrashDataSource {
    fun listTrash(accountName: String): List<HCTrashItem>

    fun restoreTrashItem(accountName: String, fileId: String, originalLocation: String, forceOverride: Boolean)

    fun deleteTrashItemPermanently(accountName: String, fileId: String)
}
