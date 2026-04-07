package com.owncloud.android.data.tags.datasources.implementation

import com.owncloud.android.data.tags.datasources.LocalTagDataSource
import com.owncloud.android.data.tags.db.OCFileTagEntity
import com.owncloud.android.data.tags.db.OCTagEntity
import com.owncloud.android.data.tags.db.TagDao
import com.owncloud.android.domain.tags.model.OCTag
import timber.log.Timber

class OCLocalTagDataSource(
    private val tagDao: TagDao,
) : LocalTagDataSource {

    override fun getTagsForAccount(accountOwner: String): List<OCTag> =
        tagDao.getTagsForAccount(accountOwner).map { it.toModel() }

    override fun getTagsForFile(fileId: Long): List<OCTag> =
        tagDao.getTagsForFile(fileId).map { it.toModel() }

    override fun getLocalTagId(accountOwner: String, serverTagId: String): Long? =
        tagDao.getTagByServerTagId(accountOwner, serverTagId)?.id

    override fun assignTagToFile(fileId: Long, tagId: Long) {
        tagDao.assignTagToFile(OCFileTagEntity(fileId = fileId, tagId = tagId))
    }

    override fun removeTagFromFile(fileId: Long, tagId: Long) {
        tagDao.removeTagFromFile(fileId = fileId, tagId = tagId)
    }

    override fun replaceTagsForAccount(accountOwner: String, tags: List<OCTag>) {
        val entities = tags.map { it.toEntity(accountOwner) }
        tagDao.upsertTags(entities)
        val remoteTagIds = tags.mapNotNull { it.id }
        if (remoteTagIds.isNotEmpty()) {
            tagDao.deleteStaleTagsForAccount(accountOwner, remoteTagIds)
        } else {
            tagDao.deleteTagsForAccount(accountOwner)
        }
    }

    override fun replaceFileAssociationsForTag(accountOwner: String, serverTagId: String, fileRemoteIds: List<String>) {
        val tagEntity = tagDao.getTagByServerTagId(accountOwner, serverTagId) ?: return
        val fileLocalIds = if (fileRemoteIds.isNotEmpty()) {
            tagDao.getFileLocalIdsByRemoteIds(fileRemoteIds)
        } else {
            emptyList()
        }
        tagDao.replaceFileAssociationsForTag(tagEntity.id, fileLocalIds)
    }

    override fun replaceTagsForFile(fileLocalId: Long, accountOwner: String, tags: List<OCTag>) {
        val validTags = tags.filter { it.id != null }
        tagDao.upsertTags(validTags.map { it.toEntity(accountOwner) })
        val localTagIds = validTags.mapNotNull { tag ->
            tagDao.getTagByServerTagId(accountOwner, tag.id!!)?.id
                ?: run {
                    Timber.w("Tag ${tag.id} not found in local DB after upsert for file $fileLocalId")
                    null
                }
        }
        tagDao.replaceTagsForFile(fileLocalId, localTagIds)
    }

    override fun saveTag(accountOwner: String, tag: OCTag) {
        tagDao.upsertTag(tag.toEntity(accountOwner))
    }

    override fun updateTag(accountOwner: String, tag: OCTag) {
        tagDao.updateTagByServerTagId(
            accountOwner = accountOwner,
            serverTagId = tag.id ?: return,
            displayName = tag.displayName,
            userVisible = tag.userVisible,
            userAssignable = tag.userAssignable,
        )
    }

    override fun deleteTag(accountOwner: String, serverTagId: String) {
        tagDao.deleteTagByServerTagId(accountOwner, serverTagId)
    }

    override fun deleteTagsForAccount(accountOwner: String) {
        tagDao.deleteTagsForAccount(accountOwner)
    }

    companion object {
        fun OCTagEntity.toModel(): OCTag =
            OCTag(
                id = tagId,
                localId = id,
                displayName = displayName,
                userVisible = userVisible,
                userAssignable = userAssignable,
            )

        fun OCTag.toEntity(accountOwner: String): OCTagEntity =
            OCTagEntity(
                tagId = id ?: "",
                accountOwner = accountOwner,
                displayName = displayName,
                userVisible = userVisible,
                userAssignable = userAssignable,
            )
    }
}
