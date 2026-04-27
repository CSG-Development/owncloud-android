package com.owncloud.android.lib.resources.users

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

/**
 * Triggers a password-reset email for the provided user email.
 *
 * Per the server contract for `POST /users/reset_password/{email}`:
 * - 204: reset password request accepted and email sent successfully
 * - 400: bad input parameter
 * - 500: backend failure with an `Error` payload
 */
class ResetPasswordRemoteOperation(
    private val email: String,
) : RemoteOperation<Unit>() {

    override fun run(client: OwnCloudClient): RemoteOperationResult<Unit> {
        val stringUrl = "${client.baseUri}$RESET_PASSWORD_PATH$email"

        return try {
            val postMethod = PostMethod(
                URL(stringUrl),
                EMPTY_BODY.toRequestBody("application/json".toMediaType()),
            ).apply {
                setReadTimeout(TIMEOUT, TimeUnit.SECONDS)
                setConnectionTimeout(TIMEOUT, TimeUnit.SECONDS)
            }

            val status = client.executeHttpMethod(postMethod)

            if (status == HttpConstants.HTTP_NO_CONTENT) {
                RemoteOperationResult<Unit>(ResultCode.OK).apply {
                    data = Unit
                    Timber.i("Password reset requested for $email - HTTP status code: $status")
                }
            } else {
                RemoteOperationResult<Unit>(postMethod).also {
                    Timber.w("Reset password failed for $email: ${it.logMessage}")
                }
            }
        } catch (e: Exception) {
            RemoteOperationResult<Unit>(e).also {
                Timber.e(it.exception, "Exception while requesting password reset for $email")
            }
        }
    }

    companion object {
        private const val RESET_PASSWORD_PATH = "/api/v1/users/reset_password/"
        private const val EMPTY_BODY = ""
        private const val TIMEOUT = 10L
    }
}
