package com.owncloud.android.data.remoteaccess.interceptor

import com.owncloud.android.data.device.CurrentDeviceStorage
import com.owncloud.android.data.remoteaccess.RemoteAccessAuthEvents
import com.owncloud.android.data.remoteaccess.RemoteAccessTokenStorage
import com.owncloud.android.data.remoteaccess.datasources.REMOTE_ACCESS_PATH_INITIATE
import com.owncloud.android.data.remoteaccess.datasources.REMOTE_ACCESS_PATH_TOKEN
import com.owncloud.android.data.remoteaccess.datasources.REMOTE_ACCESS_PATH_TOKEN_REFRESH
import com.owncloud.android.data.remoteaccess.datasources.RemoteAccessService
import com.owncloud.android.data.remoteaccess.remote.RemoteRefreshTokenRequest
import com.owncloud.android.domain.GetFirebaseInstallationIdUseCase
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import timber.log.Timber

/**
 * OkHttp Interceptor for handling token refresh on 401/403 errors.
 *
 * Aligned with the reference algorithm "session invalid" semantics:
 *  - On 401/403, attempt a single token refresh and, on success, replay the request.
 *  - If the refresh fails (network error, refresh token rejected, etc.) we DO NOT replay
 *    the original request again with the known-bad token. Instead we synthesize a 401
 *    response and notify [RemoteAccessAuthEvents.notifySessionInvalid] so higher-level
 *    components (the auth use cases) can prompt the user to reauthenticate.
 */
class RemoteAccessTokenRefreshInterceptor(
    private val tokenStorage: RemoteAccessTokenStorage,
    @Suppress("unused") private val currentDeviceStorage: CurrentDeviceStorage,
    private val remoteAccessServiceLazy: Lazy<RemoteAccessService>,
    private val getFirebaseInstallationIdUseCase: Lazy<GetFirebaseInstallationIdUseCase>,
    private val authEvents: RemoteAccessAuthEvents,
) : Interceptor {

    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)

        if ((response.code == 401 || response.code == 403) && shouldAttemptRefresh(originalRequest)) {
            Timber.d("Received ${response.code} error, attempting token refresh")
            response.close()

            val refreshedRequest = attemptTokenRefresh(originalRequest)

            return if (refreshedRequest != null) {
                Timber.d("Retrying request with refreshed token")
                chain.proceed(refreshedRequest)
            } else {
                Timber.w("Token refresh failed, notifying session invalid")
                authEvents.notifySessionInvalid()
                buildSessionInvalidResponse(originalRequest)
            }
        }

        return response
    }

    private fun shouldAttemptRefresh(request: Request): Boolean {
        val path = request.url.encodedPath
        if (path.contains(REMOTE_ACCESS_PATH_INITIATE) ||
            path.contains(REMOTE_ACCESS_PATH_TOKEN) ||
            path.contains(REMOTE_ACCESS_PATH_TOKEN_REFRESH)
        ) {
            return false
        }
        if (request.header(RETRY_HEADER) != null) {
            Timber.w("Request already retried once, not attempting another refresh")
            return false
        }
        return true
    }

    private fun attemptTokenRefresh(originalRequest: Request): Request? {
        val currentToken = tokenStorage.getAccessToken()
        val refreshToken = tokenStorage.getRefreshToken()

        if (refreshToken.isNullOrEmpty()) {
            Timber.w("No refresh token available, cannot refresh")
            return null
        }

        synchronized(refreshLock) {
            val newToken = tokenStorage.getAccessToken()
            if (newToken != currentToken && !newToken.isNullOrEmpty()) {
                Timber.d("Token was already refreshed by another thread, using new token")
                return buildRequestWithToken(originalRequest, newToken)
            }

            return try {
                Timber.d("Attempting to refresh access token")
                val tokenResponse = runBlocking {
                    remoteAccessServiceLazy.value.refreshToken(
                        RemoteRefreshTokenRequest(
                            refreshToken = refreshToken,
                            clientId = getFirebaseInstallationIdUseCase.value.getInstallationId()
                        )
                    )
                }

                tokenStorage.saveToken(
                    accessToken = tokenResponse.accessToken,
                    refreshToken = tokenResponse.refreshToken
                )

                Timber.d("Token refreshed successfully")
                buildRequestWithToken(originalRequest, tokenResponse.accessToken)
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh token")
                if (e is retrofit2.HttpException && (e.code() == 401 || e.code() == 403)) {
                    Timber.w("Refresh token is invalid, clearing tokens")
                    tokenStorage.clearTokens()
                }
                null
            }
        }
    }

    private fun buildRequestWithToken(originalRequest: Request, token: String): Request {
        return originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .header(RETRY_HEADER, "true")
            .build()
    }

    /**
     * Build a synthetic 401 response so callers see a clear "unauthorized" signal without
     * us having to send another doomed network request.
     */
    private fun buildSessionInvalidResponse(originalRequest: Request): Response {
        return Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Remote Access session invalid")
            .body("".toResponseBody(null))
            .build()
    }

    companion object {
        private const val RETRY_HEADER = "X-Token-Retry"
    }
}
