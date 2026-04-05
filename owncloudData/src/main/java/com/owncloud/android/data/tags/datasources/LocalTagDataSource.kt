package com.owncloud.android.data.tags.datasources

import com.owncloud.android.domain.tags.model.OCTag

interface LocalTagDataSource {
    fun getTagsForAccount(accountOwner: String): List<OCTag>
    fun getTagsForFile(fileId: Long): List<OCTag>
    fun getFileIdsByTag(tagId: Long): List<Long>
    fun getLocalTagId(accountOwner: String, serverTagId: String): Long?
    fun assignTagToFile(fileId: Long, tagId: Long)
    fun removeTagFromFile(fileId: Long, tagId: Long)
    fun replaceTagsForAccount(accountOwner: String, tags: List<OCTag>)
    fun replaceFileAssociationsForTag(accountOwner: String, serverTagId: String, fileRemoteIds: List<String>)
    fun replaceTagsForFile(fileLocalId: Long, accountOwner: String, tags: List<OCTag>)
    fun saveTag(accountOwner: String, tag: OCTag)
    fun updateTag(accountOwner: String, tag: OCTag)
    fun deleteTag(accountOwner: String, serverTagId: String)
    fun deleteTagsForAccount(accountOwner: String)
}
