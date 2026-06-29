package com.owncloud.android.lib.resources.trash

import at.bitfire.dav4jvm.PropStat
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyRegistry
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.property.GetContentLength
import at.bitfire.dav4jvm.property.GetContentType
import at.bitfire.dav4jvm.property.GetLastModified
import at.bitfire.dav4jvm.property.ResourceType
import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.accounts.AccountUtils
import com.owncloud.android.lib.common.http.HttpConstants
import com.owncloud.android.lib.common.http.HttpConstants.HTTP_MULTI_STATUS
import com.owncloud.android.lib.common.http.HttpConstants.HTTP_OK
import com.owncloud.android.lib.common.http.methods.webdav.DavConstants
import com.owncloud.android.lib.common.http.methods.webdav.DavUtils
import com.owncloud.android.lib.common.http.methods.webdav.PropfindMethod
import com.owncloud.android.lib.common.http.methods.webdav.properties.HCTrashbinDeleteDatetime
import com.owncloud.android.lib.common.http.methods.webdav.properties.HCTrashbinDeleteTimestamp
import com.owncloud.android.lib.common.http.methods.webdav.properties.HCTrashbinOriginalFilename
import com.owncloud.android.lib.common.http.methods.webdav.properties.HCTrashbinOriginalLocation
import com.owncloud.android.lib.common.operations.RemoteOperation
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import com.owncloud.android.lib.common.utils.isOneOf
import com.owncloud.android.lib.resources.files.FileUtils.MIME_DIR
import timber.log.Timber

class ReadRemoteTrashOperation : RemoteOperation<List<RemoteTrashItem>>() {

    override fun run(client: OwnCloudClient): RemoteOperationResult<List<RemoteTrashItem>> {
        try {
            registerTrashProperties()

            val userId = AccountUtils.getUserId(mAccount, mContext)
            val propfindMethod = PropfindMethod(
                HCTrashUtils.getTrashBinWebDavUrl(client),
                DavConstants.DEPTH_1,
                trashPropSet,
            )

            val status = client.executeHttpMethod(propfindMethod)

            return if (isSuccess(status)) {
                val items = propfindMethod.members.mapNotNull { resource ->
                    getRemoteTrashItemFromDav(resource, userId)
                }
                RemoteOperationResult<List<RemoteTrashItem>>(ResultCode.OK).apply {
                    data = items
                    Timber.i("Listed trash bin with ${items.size} items - HTTP status code: $status")
                }
            } else {
                RemoteOperationResult<List<RemoteTrashItem>>(propfindMethod).also {
                    Timber.w("List trash bin failed: ${it.logMessage}")
                }
            }
        } catch (e: Exception) {
            return RemoteOperationResult<List<RemoteTrashItem>>(e).also {
                Timber.e(it.exception, "List trash bin failed")
            }
        }
    }

    private fun isSuccess(status: Int): Boolean = status.isOneOf(HTTP_OK, HTTP_MULTI_STATUS)

    companion object {
        private val trashPropSet = DavUtils.allPropSet + setOf(
            HCTrashbinOriginalFilename.NAME,
            HCTrashbinOriginalLocation.NAME,
            HCTrashbinDeleteDatetime.NAME,
            HCTrashbinDeleteTimestamp.NAME,
        )

        private fun registerTrashProperties() {
            PropertyRegistry.register(HCTrashbinOriginalFilename.Factory())
            PropertyRegistry.register(HCTrashbinOriginalLocation.Factory())
            PropertyRegistry.register(HCTrashbinDeleteDatetime.Factory())
            PropertyRegistry.register(HCTrashbinDeleteTimestamp.Factory())
        }

        fun getRemoteTrashItemFromDav(resource: Response, userId: String): RemoteTrashItem? {
            val href = resource.href.toString()
            val fileId = href.trimEnd('/').substringAfterLast('/')
            if (fileId.isBlank() || fileId == userId || !href.contains("/trash-bin/")) {
                return null
            }

            var isFolder: Boolean = false
            var originalFilename: String? = null
            var originalLocation: String? = null
            var deletedAt: String? = null
            var deletedTimestamp: Long? = null
            var contentLength = 0L
            var mimeType: String? = null
            var lastModified: String? = null

            for (property in getProperties(resource)) {
                when (property) {
                    is ResourceType -> isFolder = property.types.contains(ResourceType.COLLECTION)
                    is HCTrashbinOriginalFilename -> originalFilename = property.value
                    is HCTrashbinOriginalLocation -> originalLocation = property.value
                    is HCTrashbinDeleteDatetime -> deletedAt = property.value
                    is HCTrashbinDeleteTimestamp -> deletedTimestamp = property.value.toLongOrNull()
                    is GetContentLength -> contentLength = property.contentLength
                    is GetContentType -> mimeType = property.type
                    is GetLastModified -> lastModified = property.lastModified.toString()
                }
            }

            val filename = originalFilename ?: return null
            val location = originalLocation ?: return null

            return RemoteTrashItem(
                fileId = fileId,
                trashDavPath = href,
                originalFilename = filename,
                originalLocation = location,
                deletedAt = deletedAt,
                deletedTimestamp = deletedTimestamp,
                contentLength = contentLength,
                mimeType = if (isFolder) MIME_DIR else mimeType,
                lastModified = lastModified,
            )
        }

        private fun getProperties(response: Response): List<Property> =
            if (response.isSuccess()) {
                response.propstat
                    .filter { propStat -> propStat.isSuccessOrPostProcessing() }
                    .flatMap { it.properties }
            } else {
                emptyList()
            }

        private fun PropStat.isSuccessOrPostProcessing() =
            status.code / 100 == 2 || status.code == HttpConstants.HTTP_TOO_EARLY
    }
}
