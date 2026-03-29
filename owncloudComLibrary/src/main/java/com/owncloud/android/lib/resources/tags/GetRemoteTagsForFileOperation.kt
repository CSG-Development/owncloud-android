package com.owncloud.android.lib.resources.tags

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyRegistry
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.property.OCId
import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.http.HttpConstants
import com.owncloud.android.lib.common.http.methods.webdav.DavConstants
import com.owncloud.android.lib.common.http.methods.webdav.PropfindMethod
import com.owncloud.android.lib.common.http.methods.webdav.properties.OCTagDisplayName
import com.owncloud.android.lib.common.http.methods.webdav.properties.OCUserAssignable
import com.owncloud.android.lib.common.http.methods.webdav.properties.OCUserVisible
import com.owncloud.android.lib.common.operations.RemoteOperation
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import timber.log.Timber
import java.net.URL
import java.util.concurrent.TimeUnit

class GetRemoteTagsForFileOperation(
    private val fileRemoteId: Long,
) : RemoteOperation<List<RemoteTag>>() {

    override fun run(client: OwnCloudClient): RemoteOperationResult<List<RemoteTag>> {
        PropertyRegistry.register(OCTagDisplayName.Factory())
        PropertyRegistry.register(OCUserVisible.Factory())
        PropertyRegistry.register(OCUserAssignable.Factory())

        val stringUrl = "${client.baseUri}$SYSTEM_TAGS_RELATIONS_PATH/$fileRemoteId"

        return try {
            val propfindMethod = PropfindMethod(
                URL(stringUrl),
                DavConstants.DEPTH_1,
                TAG_PROP_SET,
            ).apply {
                setReadTimeout(TIMEOUT, TimeUnit.SECONDS)
                setConnectionTimeout(TIMEOUT, TimeUnit.SECONDS)
            }

            val status = client.executeHttpMethod(propfindMethod)

            if (isSuccess(status)) {
                val tags = propfindMethod.members.map { response -> parseTag(response) }
                RemoteOperationResult<List<RemoteTag>>(ResultCode.OK).apply {
                    data = tags
                    Timber.i("Retrieved ${tags.size} tags for file $fileRemoteId - HTTP status: $status")
                }
            } else {
                RemoteOperationResult<List<RemoteTag>>(propfindMethod).also {
                    Timber.w("Get tags for file failed: ${it.logMessage}")
                }
            }
        } catch (e: Exception) {
            RemoteOperationResult<List<RemoteTag>>(e).also {
                Timber.e(it.exception, "Exception while getting tags for file $fileRemoteId")
            }
        }
    }

    private fun parseTag(response: Response): RemoteTag {
        val remoteTag = RemoteTag()
        response.propstat
            .filter { it.isSuccess() }
            .flatMap { it.properties }
            .forEach { property ->
                when (property) {
                    is OCId -> remoteTag.id = property.id
                    is OCTagDisplayName -> remoteTag.displayName = property.displayName
                    is OCUserVisible -> remoteTag.userVisible = property.userVisible
                    is OCUserAssignable -> remoteTag.userAssignable = property.userAssignable
                }
            }
        return remoteTag
    }

    private fun isSuccess(status: Int) =
        status == HttpConstants.HTTP_OK || status == HttpConstants.HTTP_MULTI_STATUS

    companion object {
        private const val SYSTEM_TAGS_RELATIONS_PATH = "/remote.php/dav/systemtags-relations/files"
        private const val TIMEOUT = 10_000L

        private val TAG_PROP_SET: Array<Property.Name> = arrayOf(
            OCTagDisplayName.NAME,
            OCUserVisible.NAME,
            OCUserAssignable.NAME,
            OCId.NAME,
        )
    }
}
