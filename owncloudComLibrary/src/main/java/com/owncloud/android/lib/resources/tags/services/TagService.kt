package com.owncloud.android.lib.resources.tags.services

import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.resources.Service
import com.owncloud.android.lib.resources.tags.RemoteTag

interface TagService : Service {
    fun getSystemTags(): RemoteOperationResult<List<RemoteTag>>
    fun getFileIdsByTag(tagId: String): RemoteOperationResult<List<String>>
    fun createTag(name: String, userVisible: Boolean, userAssignable: Boolean): RemoteOperationResult<String>
    fun updateTag(tagId: String, displayName: String?, userVisible: Boolean?, userAssignable: Boolean?): RemoteOperationResult<Unit>
    fun deleteTag(tagId: String): RemoteOperationResult<Unit>
    fun assignTagToFile(fileRemoteId: Long, tagId: String): RemoteOperationResult<Unit>
    fun unassignTagFromFile(fileRemoteId: Long, tagId: String): RemoteOperationResult<Unit>
    fun getTagsForFile(fileRemoteId: Long): RemoteOperationResult<List<RemoteTag>>
}
