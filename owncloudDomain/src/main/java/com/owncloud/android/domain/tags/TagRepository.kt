package com.owncloud.android.domain.tags

import com.owncloud.android.domain.tags.model.OCTag

interface TagRepository {
    fun getTagsForAccount(accountName: String): List<OCTag>
    fun getLocalTagsForFile(fileLocalId: Long): List<OCTag>
    fun assignTagToFile(accountName: String, fileLocalId: Long, fileRemoteId: Long, tagId: String)
    fun removeTagFromFile(accountName: String, fileLocalId: Long, fileRemoteId: Long, tagId: String)
    fun refreshTagsForAccount(accountName: String): List<OCTag>
    fun refreshFilesByTag(accountName: String, serverTagId: String): List<String>
    fun refreshTagsForFile(accountName: String, fileRemoteId: Long, fileLocalId: Long): List<OCTag>
    fun createTag(accountName: String, name: String, userVisible: Boolean, userAssignable: Boolean)
    fun updateTag(accountName: String, tagId: String, displayName: String?, userVisible: Boolean?, userAssignable: Boolean?)
    fun deleteTag(accountName: String, tagId: String)
}
