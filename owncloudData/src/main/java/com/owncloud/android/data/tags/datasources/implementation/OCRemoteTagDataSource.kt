package com.owncloud.android.data.tags.datasources.implementation

import com.owncloud.android.data.ClientManager
import com.owncloud.android.data.executeRemoteOperation
import com.owncloud.android.data.tags.datasources.RemoteTagDataSource
import com.owncloud.android.domain.tags.model.OCTag
import com.owncloud.android.lib.resources.tags.RemoteTag

class OCRemoteTagDataSource(
    private val clientManager: ClientManager,
) : RemoteTagDataSource {

    override fun getSystemTags(accountName: String): List<OCTag> =
        executeRemoteOperation {
            clientManager.getTagService(accountName).getSystemTags()
        }.map { it.toModel() }

    override fun getFileRemoteIdsByTag(accountName: String, tagId: String): List<String> =
        executeRemoteOperation {
            clientManager.getTagService(accountName).getFileIdsByTag(tagId)
        }

    companion object {
        fun RemoteTag.toModel(): OCTag =
            OCTag(
                id = id,
                displayName = displayName,
                userVisible = userVisible,
                userAssignable = userAssignable,
            )
    }
}
