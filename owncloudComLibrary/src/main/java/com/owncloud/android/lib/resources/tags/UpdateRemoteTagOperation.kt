package com.owncloud.android.lib.resources.tags

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.http.HttpConstants
import com.owncloud.android.lib.common.http.methods.nonwebdav.ProppatchMethod
import com.owncloud.android.lib.common.operations.RemoteOperation
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.URL
import java.util.concurrent.TimeUnit

class UpdateRemoteTagOperation(
    private val tagId: String,
    private val displayName: String?,
    private val userVisible: Boolean?,
    private val userAssignable: Boolean?,
) : RemoteOperation<Unit>() {

    override fun run(client: OwnCloudClient): RemoteOperationResult<Unit> {
        val stringUrl = "${client.baseUri}$SYSTEM_TAGS_PATH/$tagId"
        val xmlBody = buildProppatchXml()

        return try {
            val proppatchMethod = ProppatchMethod(
                URL(stringUrl),
                xmlBody.toRequestBody("text/xml".toMediaType()),
            ).apply {
                setReadTimeout(TIMEOUT, TimeUnit.SECONDS)
                setConnectionTimeout(TIMEOUT, TimeUnit.SECONDS)
            }

            val status = client.executeHttpMethod(proppatchMethod)

            if (isSuccess(status)) {
                RemoteOperationResult<Unit>(ResultCode.OK).apply {
                    data = Unit
                    Timber.i("Updated tag $tagId - HTTP status code: $status")
                }
            } else {
                RemoteOperationResult<Unit>(proppatchMethod).also {
                    Timber.w("Update tag failed: ${it.logMessage}")
                }
            }
        } catch (e: Exception) {
            RemoteOperationResult<Unit>(e).also {
                Timber.e(it.exception, "Exception while updating tag $tagId")
            }
        }
    }

    private fun buildProppatchXml(): String {
        val props = buildString {
            displayName?.let { append("<oc:display-name>$it</oc:display-name>") }
            userVisible?.let { append("<oc:user-visible>$it</oc:user-visible>") }
            userAssignable?.let { append("<oc:user-assignable>$it</oc:user-assignable>") }
        }
        return """<?xml version="1.0" encoding="utf-8" ?>
            |<a:propertyupdate xmlns:a="DAV:" xmlns:oc="http://owncloud.org/ns">
            |  <a:set>
            |    <a:prop>$props</a:prop>
            |  </a:set>
            |</a:propertyupdate>""".trimMargin()
    }

    private fun isSuccess(status: Int) =
        status == HttpConstants.HTTP_OK || status == HttpConstants.HTTP_MULTI_STATUS

    companion object {
        private const val SYSTEM_TAGS_PATH = "/remote.php/dav/systemtags"
        private const val TIMEOUT = 10L
    }
}
