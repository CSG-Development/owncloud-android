package com.owncloud.android.lib.resources.tags

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.http.HttpConstants
import com.owncloud.android.lib.common.http.methods.nonwebdav.PutMethod
import com.owncloud.android.lib.common.operations.RemoteOperation
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.URL
import java.util.concurrent.TimeUnit

class AssignTagToFileRemoteOperation(
    private val fileRemoteId: Long,
    private val tagId: String,
) : RemoteOperation<Unit>() {

    override fun run(client: OwnCloudClient): RemoteOperationResult<Unit> {
        val stringUrl = "${client.baseUri}$SYSTEM_TAGS_RELATIONS_PATH/$fileRemoteId/$tagId"

        return try {
            val putMethod = PutMethod(URL(stringUrl), ByteArray(0).toRequestBody()).apply {
                setReadTimeout(TIMEOUT, TimeUnit.SECONDS)
                setConnectionTimeout(TIMEOUT, TimeUnit.SECONDS)
            }

            val status = client.executeHttpMethod(putMethod)

            if (status == HttpConstants.HTTP_CREATED || status == HttpConstants.HTTP_CONFLICT) {
                RemoteOperationResult<Unit>(ResultCode.OK).apply {
                    data = Unit
                    Timber.i("Assigned tag $tagId to file $fileRemoteId - HTTP status code: $status")
                }
            } else {
                RemoteOperationResult<Unit>(putMethod).also {
                    Timber.w("Assign tag to file failed: ${it.logMessage}")
                }
            }
        } catch (e: Exception) {
            RemoteOperationResult<Unit>(e).also {
                Timber.e(it.exception, "Exception while assigning tag $tagId to file $fileRemoteId")
            }
        }
    }

    companion object {
        private const val SYSTEM_TAGS_RELATIONS_PATH = "/remote.php/dav/systemtags-relations/files"
        private const val TIMEOUT = 10L
    }
}
