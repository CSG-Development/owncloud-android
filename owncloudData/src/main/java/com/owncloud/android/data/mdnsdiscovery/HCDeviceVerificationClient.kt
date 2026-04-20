package com.owncloud.android.data.mdnsdiscovery

import com.owncloud.android.data.mdnsdiscovery.remote.HCDeviceAboutResponse
import com.owncloud.android.data.mdnsdiscovery.remote.HCDeviceStatusResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * HTTP client for verifying discovered devices.
 *
 * Per the reference algorithm probe budget:
 *  - LOCAL probes use [LOCAL_TIMEOUT_MS] (4s) so a non-responsive LAN candidate fails fast.
 *  - non-LOCAL probes (PUBLIC/REMOTE) use [NON_LOCAL_TIMEOUT_MS] (9s) since WAN latency is
 *    higher and more variable.
 *
 * Timeouts are applied at the call level via [OkHttpClient.Builder.callTimeout] on a thin
 * derived client, so they do not mutate the shared injected client.
 */
class HCDeviceVerificationClient(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
) {

    private val statusAdapter by lazy {
        moshi.adapter(HCDeviceStatusResponse::class.java)
    }

    private val aboutAdapter by lazy {
        moshi.adapter(HCDeviceAboutResponse::class.java)
    }

    private val localClient: OkHttpClient by lazy {
        okHttpClient.newBuilder().callTimeout(LOCAL_TIMEOUT_MS, TimeUnit.MILLISECONDS).build()
    }

    private val nonLocalClient: OkHttpClient by lazy {
        okHttpClient.newBuilder().callTimeout(NON_LOCAL_TIMEOUT_MS, TimeUnit.MILLISECONDS).build()
    }

    /**
     * Verifies if a device is alive by checking the `/api/v1/status` endpoint.
     *
     * @param deviceUrl The base URL of the device (e.g. `https://192.168.1.100:8080`).
     * @param isLocal When true, applies the local timeout budget (4s); otherwise the
     *  non-local budget (9s) is used.
     * @return true if the device responds with a valid status indicating it's ready.
     */
    suspend fun verifyDevice(deviceUrl: String, isLocal: Boolean = true): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val statusUrl = "$deviceUrl/api/v1/status"
                val responseBody = makeRequest(statusUrl, clientFor(isLocal))
                if (responseBody == null) {
                    Timber.w("Device verification failed: empty response body for: $deviceUrl")
                    return@withContext false
                }

                val statusResponse = statusAdapter.fromJson(responseBody)
                if (statusResponse == null) {
                    Timber.w("Device verification failed: unable to parse response for: $deviceUrl")
                    return@withContext false
                }

                val isReady = statusResponse.oobe.done && statusResponse.apps.files == "ready"

                if (isReady) {
                    Timber.d("Device verified successfully: $deviceUrl (isLocal=$isLocal)")
                } else {
                    Timber.w("Device not ready: $deviceUrl - $statusResponse")
                }
                isReady
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Device verification failed for: $deviceUrl")
                false
            }
        }
    }

    /**
     * Fetches the certificate common name from the `/api/v1/about` endpoint.
     *
     * @param deviceUrl The base URL of the device (e.g. `https://192.168.1.100:8080`).
     * @param isLocal When true, applies the local timeout (4s); otherwise the non-local
     *  timeout (9s).
     */
    suspend fun getCertificateCommonName(deviceUrl: String, isLocal: Boolean = true): String? {
        return withContext(Dispatchers.IO) {
            try {
                val aboutUrl = "$deviceUrl/api/v1/about"
                Timber.d("Fetching about info from: $aboutUrl (isLocal=$isLocal)")

                val responseBody = makeRequest(aboutUrl, clientFor(isLocal))
                if (responseBody == null) {
                    Timber.w("Failed to fetch about info: empty response body for: $deviceUrl")
                    return@withContext null
                }

                val aboutResponse = aboutAdapter.fromJson(responseBody)
                if (aboutResponse == null) {
                    Timber.w("Failed to fetch about info: unable to parse response for: $deviceUrl")
                    return@withContext null
                }

                aboutResponse.certificateCommonName
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch about info for: $deviceUrl")
                null
            }
        }
    }

    private fun clientFor(isLocal: Boolean): OkHttpClient = if (isLocal) localClient else nonLocalClient

    private fun makeRequest(url: String, client: OkHttpClient): String? {
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Timber.w("Failed to fetch ${request.url} with HTTP ${response.code}")
            return null
        }
        return response.body?.string()
    }

    companion object {
        const val LOCAL_TIMEOUT_MS: Long = 4_000L
        const val NON_LOCAL_TIMEOUT_MS: Long = 9_000L
    }
}
