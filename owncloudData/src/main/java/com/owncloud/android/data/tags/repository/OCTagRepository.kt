package com.owncloud.android.data.tags.repository

import com.owncloud.android.data.tags.datasources.LocalTagDataSource
import com.owncloud.android.data.tags.datasources.RemoteTagDataSource
import com.owncloud.android.domain.tags.TagRepository
import com.owncloud.android.domain.tags.model.OCTag

class OCTagRepository(
    private val remoteTagDataSource: RemoteTagDataSource,
    private val localTagDataSource: LocalTagDataSource,
) : TagRepository {

    override fun getLocalTagsForAccount(accountName: String): List<OCTag> =
        localTagDataSource.getTagsForAccount(accountName)

    override fun getTagsByLocalIds(localIds: List<Long>): List<OCTag> =
        localTagDataSource.getTagsByLocalIds(localIds)

    override fun getLocalTagsForFile(fileLocalId: Long): List<OCTag> =
        localTagDataSource.getTagsForFile(fileLocalId)

    override fun assignTagToFile(accountName: String, fileLocalId: Long, fileRemoteId: Long, tagId: String) {
        remoteTagDataSource.assignTagToFile(accountName, fileRemoteId, tagId)
        val localTagId = localTagDataSource.getLocalTagId(accountName, tagId) ?: return
        localTagDataSource.assignTagToFile(fileLocalId, localTagId)
    }

    override fun removeTagFromFile(accountName: String, fileLocalId: Long, fileRemoteId: Long, tagId: String) {
        remoteTagDataSource.unassignTagFromFile(accountName, fileRemoteId, tagId)
        val localTagId = localTagDataSource.getLocalTagId(accountName, tagId) ?: return
        localTagDataSource.removeTagFromFile(fileLocalId, localTagId)
    }

    override fun refreshTagsForAccount(accountName: String): List<OCTag> {
        try {
            val remoteTags = remoteTagDataSource.getSystemTags(accountName)
            localTagDataSource.replaceTagsForAccount(accountName, remoteTags)
        } catch (_: Throwable) {
            // Fall through to local read below
        }
        return localTagDataSource.getTagsForAccount(accountName)
    }

    override fun refreshFilesByTag(accountName: String, serverTagId: String): List<String> {
        val remoteFileIds = remoteTagDataSource.getFileRemoteIdsByTag(accountName, serverTagId)
        localTagDataSource.replaceFileAssociationsForTag(accountName, serverTagId, remoteFileIds)
        return remoteFileIds
    }

    override fun refreshTagsForFile(accountName: String, fileRemoteId: Long, fileLocalId: Long): List<OCTag> {
        val tags = remoteTagDataSource.getRemoteTagsForFile(accountName, fileRemoteId)
        localTagDataSource.replaceTagsForFile(fileLocalId, accountName, tags)
        return tags
    }

    override fun createTag(accountName: String, name: String, userVisible: Boolean, userAssignable: Boolean) {
        val newTagId = remoteTagDataSource.createTag(accountName, name, userVisible, userAssignable)
        localTagDataSource.saveTag(
            accountName,
            OCTag(id = newTagId, displayName = name, userVisible = userVisible, userAssignable = userAssignable)
        )
    }

    override fun updateTag(accountName: String, tagId: String, displayName: String?, userVisible: Boolean?, userAssignable: Boolean?) {
        remoteTagDataSource.updateTag(accountName, tagId, displayName, userVisible, userAssignable)
        val existingTags = localTagDataSource.getTagsForAccount(accountName)
        val currentTag = existingTags.firstOrNull { it.id == tagId }
        if (currentTag != null) {
            localTagDataSource.updateTag(
                accountName,
                OCTag(
                    id = tagId,
                    displayName = displayName ?: currentTag.displayName,
                    userVisible = userVisible ?: currentTag.userVisible,
                    userAssignable = userAssignable ?: currentTag.userAssignable,
                )
            )
        }
    }

    override fun deleteTag(accountName: String, tagId: String) {
        remoteTagDataSource.deleteTag(accountName, tagId)
        localTagDataSource.deleteTag(accountName, tagId)
    }
}
