package com.owncloud.android.lib.resources.tags

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.http.HttpConstants
import com.owncloud.android.lib.common.http.methods.nonwebdav.PostMethod
import com.owncloud.android.lib.common.operations.RemoteOperation
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.URL
import java.util.concurrent.TimeUnit

class CreateRemoteTagOperation(
    private val name: String,
    private val userVisible: Boolean = true,
    private val userAssignable: Boolean = true,
) : RemoteOperation<String>() {

    override fun run(client: OwnCloudClient): RemoteOperationResult<String> {
        val stringUrl = "${client.baseUri}$SYSTEM_TAGS_PATH"
        val json = """{"name":"$name","userVisible":"$userVisible","userAssignable":"$userAssignable"}"""

        return try {
            val postMethod = PostMethod(
                URL(stringUrl),
                json.toRequestBody("application/json".toMediaType()),
            ).apply {
                setReadTimeout(TIMEOUT, TimeUnit.SECONDS)
                setConnectionTimeout(TIMEOUT, TimeUnit.SECONDS)
            }

            val status = client.executeHttpMethod(postMethod)

            if (status == HttpConstants.HTTP_CREATED) {
                val contentLocation = postMethod.getResponseHeader("Content-Location") ?: ""
                val tagId = contentLocation.substringAfterLast("/")
                RemoteOperationResult<String>(ResultCode.OK).apply {
                    data = tagId
                    Timber.i("Created tag '$name' with id $tagId - HTTP status code: $status")
                }
            } else {
                RemoteOperationResult<String>(postMethod).also {
                    Timber.w("Create tag failed: ${it.logMessage}")
                }
            }
        } catch (e: Exception) {
            RemoteOperationResult<String>(e).also {
                Timber.e(it.exception, "Exception while creating tag '$name'")
            }
        }
    }

    companion object {
        private const val SYSTEM_TAGS_PATH = "/remote.php/dav/systemtags"
        private const val TIMEOUT = 10L
    }
}
