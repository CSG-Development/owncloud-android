package com.owncloud.android.data.tags.repository

import com.owncloud.android.data.tags.datasources.LocalTagDataSource
import com.owncloud.android.data.tags.datasources.RemoteTagDataSource
import com.owncloud.android.domain.tags.TagRepository
import com.owncloud.android.domain.tags.model.OCTag

class OCTagRepository(
    private val remoteTagDataSource: RemoteTagDataSource,
    private val localTagDataSource: LocalTagDataSource,
) : TagRepository {

    override fun getTagsForAccount(accountName: String): List<OCTag> =
        remoteTagDataSource.getSystemTags(accountName)

    override fun getTagsForFile(fileId: Long): List<OCTag> =
        localTagDataSource.getTagsForFile(fileId)

    override fun getFileIdsByTag(tagId: Long): List<Long> =
        localTagDataSource.getFileIdsByTag(tagId)

    override fun assignTagToFile(fileId: Long, tagId: Long) =
        localTagDataSource.assignTagToFile(fileId, tagId)

    override fun removeTagFromFile(fileId: Long, tagId: Long) =
        localTagDataSource.removeTagFromFile(fileId, tagId)

    override fun refreshTagsForAccount(accountName: String): List<OCTag> {
        val remoteTags = remoteTagDataSource.getSystemTags(accountName)
        localTagDataSource.replaceTagsForAccount(accountName, remoteTags)
        return remoteTags
    }

    override fun refreshFilesByTag(accountName: String, serverTagId: String): List<String> {
        val remoteFileIds = remoteTagDataSource.getFileRemoteIdsByTag(accountName, serverTagId)
        localTagDataSource.replaceFileAssociationsForTag(accountName, serverTagId, remoteFileIds)
        return remoteFileIds
    }
}
