package com.owncloud.android.lib.resources.trash

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.http.HttpConstants
import com.owncloud.android.lib.common.http.methods.nonwebdav.DeleteMethod
import com.owncloud.android.lib.common.operations.RemoteOperation
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import com.owncloud.android.lib.common.utils.isOneOf
import timber.log.Timber

class DeleteRemoteTrashItemOperation(
    private val fileId: String,
) : RemoteOperation<Unit>() {

    override fun run(client: OwnCloudClient): RemoteOperationResult<Unit> {
        var result: RemoteOperationResult<Unit>
        try {
            val deleteMethod = DeleteMethod(HCTrashUtils.getTrashItemWebDavUrl(client, fileId))
            val status = client.executeHttpMethod(deleteMethod)

            result = if (isSuccess(status)) {
                RemoteOperationResult<Unit>(ResultCode.OK)
            } else {
                RemoteOperationResult<Unit>(deleteMethod)
            }
            Timber.i("Permanently delete trash item $fileId - HTTP status code: $status")
        } catch (e: Exception) {
            result = RemoteOperationResult<Unit>(e)
            Timber.e(e, "Permanently delete trash item $fileId failed: ${result.logMessage}")
        }
        return result
    }

    private fun isSuccess(status: Int): Boolean =
        status.isOneOf(HttpConstants.HTTP_OK, HttpConstants.HTTP_NO_CONTENT)
}
