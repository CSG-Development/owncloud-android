package com.owncloud.android.lib.resources.tags.services.implementation

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.resources.tags.AssignTagToFileRemoteOperation
import com.owncloud.android.lib.resources.tags.CreateRemoteTagOperation
import com.owncloud.android.lib.resources.tags.DeleteRemoteTagOperation
import com.owncloud.android.lib.resources.tags.GetRemoteFilesByTagOperation
import com.owncloud.android.lib.resources.tags.GetRemoteTagsForFileOperation
import com.owncloud.android.lib.resources.tags.ListRemoteTagsOperation
import com.owncloud.android.lib.resources.tags.RemoteTag
import com.owncloud.android.lib.resources.tags.UnassignTagFromFileRemoteOperation
import com.owncloud.android.lib.resources.tags.UpdateRemoteTagOperation
import com.owncloud.android.lib.resources.tags.services.TagService

class OCTagService(override val client: OwnCloudClient) : TagService {
    override fun getSystemTags(): RemoteOperationResult<List<RemoteTag>> =
        ListRemoteTagsOperation().execute(client)

    override fun getFileIdsByTag(tagId: String): RemoteOperationResult<List<String>> =
        GetRemoteFilesByTagOperation(tagId).execute(client)

    override fun createTag(name: String, userVisible: Boolean, userAssignable: Boolean): RemoteOperationResult<String> =
        CreateRemoteTagOperation(name, userVisible, userAssignable).execute(client)

    override fun updateTag(tagId: String, displayName: String?, userVisible: Boolean?, userAssignable: Boolean?): RemoteOperationResult<Unit> =
        UpdateRemoteTagOperation(tagId, displayName, userVisible, userAssignable).execute(client)

    override fun deleteTag(tagId: String): RemoteOperationResult<Unit> =
        DeleteRemoteTagOperation(tagId).execute(client)

    override fun assignTagToFile(fileRemoteId: Long, tagId: String): RemoteOperationResult<Unit> =
        AssignTagToFileRemoteOperation(fileRemoteId, tagId).execute(client)

    override fun unassignTagFromFile(fileRemoteId: Long, tagId: String): RemoteOperationResult<Unit> =
        UnassignTagFromFileRemoteOperation(fileRemoteId, tagId).execute(client)

    override fun getTagsForFile(fileRemoteId: Long): RemoteOperationResult<List<RemoteTag>> =
        GetRemoteTagsForFileOperation(fileRemoteId).execute(client)
}
