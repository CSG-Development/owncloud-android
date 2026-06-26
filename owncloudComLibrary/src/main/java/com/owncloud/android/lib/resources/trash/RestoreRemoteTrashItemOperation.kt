package com.owncloud.android.lib.resources.trash

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.http.HttpConstants
import com.owncloud.android.lib.common.http.methods.webdav.MoveMethod
import com.owncloud.android.lib.common.network.WebdavUtils
import com.owncloud.android.lib.common.operations.RemoteOperation
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import com.owncloud.android.lib.common.utils.isOneOf
import timber.log.Timber
import java.util.concurrent.TimeUnit

class RestoreRemoteTrashItemOperation(
    private val fileId: String,
    private val originalLocation: String,
    private val forceOverride: Boolean = false,
) : RemoteOperation<Unit>() {

    override fun run(client: OwnCloudClient): RemoteOperationResult<Unit> {
        var result: RemoteOperationResult<Unit>
        try {
            val restorePath = HCTrashUtils.originalLocationToRemotePath(originalLocation)
            val destinationUrl = client.userFilesWebDavUri.toString() + WebdavUtils.encodePath(restorePath)
            val moveMethod = MoveMethod(
                url = HCTrashUtils.getTrashItemWebDavUrl(client, fileId),
                destinationUrl = destinationUrl,
                forceOverride = forceOverride,
            ).apply {
                if (forceOverride) {
                    setRequestHeader(OVERWRITE, TRUE)
                } else {
                    setRequestHeader(OVERWRITE, FALSE)
                }
                setReadTimeout(MOVE_READ_TIMEOUT, TimeUnit.SECONDS)
                setConnectionTimeout(MOVE_CONNECTION_TIMEOUT, TimeUnit.SECONDS)
            }

            val status = client.executeHttpMethod(moveMethod)

            result = when {
                isSuccess(status) -> RemoteOperationResult<Unit>(ResultCode.OK)
                isPreconditionFailed(status) -> {
                    client.exhaustResponse(moveMethod.getResponseBodyAsStream())
                    RemoteOperationResult<Unit>(ResultCode.INVALID_OVERWRITE)
                }
                else -> {
                    RemoteOperationResult<Unit>(moveMethod).also {
                        client.exhaustResponse(moveMethod.getResponseBodyAsStream())
                    }
                }
            }
            Timber.i("Restore trash item $fileId to $restorePath - HTTP status code: $status")
        } catch (e: Exception) {
            result = RemoteOperationResult<Unit>(e)
            Timber.e(e, "Restore trash item $fileId failed: ${result.logMessage}")
        }
        return result
    }

    private fun isSuccess(status: Int): Boolean =
        status.isOneOf(HttpConstants.HTTP_CREATED, HttpConstants.HTTP_NO_CONTENT)

    private fun isPreconditionFailed(status: Int): Boolean =
        status == HttpConstants.HTTP_PRECONDITION_FAILED

    companion object {
        private const val MOVE_READ_TIMEOUT = 10L
        private const val MOVE_CONNECTION_TIMEOUT = 6L
        private const val OVERWRITE = "Overwrite"
        private const val TRUE = "T"
        private const val FALSE = "F"
    }
}
