package com.owncloud.android.lib.resources.tags

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.http.HttpConstants
import com.owncloud.android.lib.common.http.methods.nonwebdav.DeleteMethod
import com.owncloud.android.lib.common.operations.RemoteOperation
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import timber.log.Timber
import java.net.URL
import java.util.concurrent.TimeUnit

class UnassignTagFromFileRemoteOperation(
    private val fileRemoteId: Long,
    private val tagId: String,
) : RemoteOperation<Unit>() {

    override fun run(client: OwnCloudClient): RemoteOperationResult<Unit> {
        val stringUrl = "${client.baseUri}$SYSTEM_TAGS_RELATIONS_PATH/$fileRemoteId/$tagId"

        return try {
            val deleteMethod = DeleteMethod(URL(stringUrl)).apply {
                setReadTimeout(TIMEOUT, TimeUnit.SECONDS)
                setConnectionTimeout(TIMEOUT, TimeUnit.SECONDS)
            }

            val status = client.executeHttpMethod(deleteMethod)

            if (status == HttpConstants.HTTP_NO_CONTENT) {
                RemoteOperationResult<Unit>(ResultCode.OK).apply {
                    data = Unit
                    Timber.i("Unassigned tag $tagId from file $fileRemoteId - HTTP status code: $status")
                }
            } else {
                RemoteOperationResult<Unit>(deleteMethod).also {
                    Timber.w("Unassign tag from file failed: ${it.logMessage}")
                }
            }
        } catch (e: Exception) {
            RemoteOperationResult<Unit>(e).also {
                Timber.e(it.exception, "Exception while unassigning tag $tagId from file $fileRemoteId")
            }
        }
    }

    companion object {
        private const val SYSTEM_TAGS_RELATIONS_PATH = "/remote.php/dav/systemtags-relations/files"
        private const val TIMEOUT = 10L
    }
}
