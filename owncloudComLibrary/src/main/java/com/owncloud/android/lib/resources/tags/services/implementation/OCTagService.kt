package com.owncloud.android.lib.resources.tags.services.implementation

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.resources.tags.ListRemoteTagsOperation
import com.owncloud.android.lib.resources.tags.RemoteTag
import com.owncloud.android.lib.resources.tags.services.TagService

class OCTagService(override val client: OwnCloudClient) : TagService {
    override fun getSystemTags(): RemoteOperationResult<List<RemoteTag>> =
        ListRemoteTagsOperation().execute(client)
}
