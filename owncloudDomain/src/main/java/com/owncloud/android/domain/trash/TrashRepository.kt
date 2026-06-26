package com.owncloud.android.domain.trash

import com.owncloud.android.domain.trash.model.HCTrashItem

interface TrashRepository {
    fun isTrashEnabled(accountName: String): Boolean

    fun listTrash(accountName: String): List<HCTrashItem>

    fun restoreTrashItem(accountName: String, fileId: String, originalLocation: String, forceOverride: Boolean)

    fun deleteTrashItemPermanently(accountName: String, fileId: String)
}
