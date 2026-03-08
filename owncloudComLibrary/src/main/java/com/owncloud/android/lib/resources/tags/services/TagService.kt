package com.owncloud.android.lib.resources.tags.services

import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.resources.Service
import com.owncloud.android.lib.resources.tags.RemoteTag

interface TagService : Service {
    fun getSystemTags(): RemoteOperationResult<List<RemoteTag>>
    fun getFileIdsByTag(tagId: String): RemoteOperationResult<List<String>>
}
