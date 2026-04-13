package com.owncloud.android.data.tags.datasources

import com.owncloud.android.domain.tags.model.OCTag

interface RemoteTagDataSource {
    fun getSystemTags(accountName: String): List<OCTag>
    fun getFileRemoteIdsByTag(accountName: String, tagId: String): List<String>
    fun createTag(accountName: String, name: String, userVisible: Boolean, userAssignable: Boolean): String
    fun updateTag(accountName: String, tagId: String, displayName: String?, userVisible: Boolean?, userAssignable: Boolean?)
    fun deleteTag(accountName: String, tagId: String)
    fun assignTagToFile(accountName: String, fileRemoteId: Long, tagId: String)
    fun unassignTagFromFile(accountName: String, fileRemoteId: Long, tagId: String)
    fun getRemoteTagsForFile(accountName: String, fileRemoteId: Long): List<OCTag>
}
