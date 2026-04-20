package com.owncloud.android.data.device

import com.owncloud.android.data.mdnsdiscovery.HCDeviceVerificationClient
import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.domain.server.usecases.DeviceUrlResolver
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import timber.log.Timber

/**
 * Implements [DeviceUrlResolver] following reference Algorithm C.
 *
 * - Probes LOCAL and PUBLIC paths in parallel.
 * - Returns the FIRST successful LOCAL probe immediately (other probes are cancelled).
 * - If every LOCAL probe fails, returns the first successful PUBLIC probe (computed once
 *   all LOCAL probes have completed, so a fast PUBLIC response cannot beat a slower
 *   LOCAL one).
 * - REMOTE/relay is intentionally NOT tested here; the chooser is responsible for the
 *   fallback step.
 * - Local probes use the 4s timeout, public probes the 9s timeout.
 */
class HCDeviceUrlResolver(
    private val deviceVerificationClient: HCDeviceVerificationClient,
) : DeviceUrlResolver {

    override suspend fun testPriorityPaths(
        paths: Map<DevicePathType, String>,
        wifiAvailable: Boolean,
    ): String? {
        if (paths.isEmpty()) {
            Timber.d("DeviceUrlResolver: no paths provided")
            return null
        }

        val localUrls = if (wifiAvailable) listOfNotNull(paths[DevicePathType.LOCAL]) else emptyList()
        val publicUrls = listOfNotNull(paths[DevicePathType.PUBLIC])

        if (localUrls.isEmpty() && publicUrls.isEmpty()) {
            Timber.d("DeviceUrlResolver: no priority paths to test (wifiAvailable=$wifiAvailable)")
            return null
        }

        return coroutineScope {
            val localDeferreds: List<Deferred<String?>> = localUrls.map { url ->
                async { probe(url, isLocal = true) }
            }
            val publicDeferreds: List<Deferred<String?>> = publicUrls.map { url ->
                async { probe(url, isLocal = false) }
            }

            // Phase 1: race local + public; first LOCAL success wins immediately.
            // Track which deferreds are still pending so we can detect "all local done".
            val pendingLocals = localDeferreds.toMutableSet()
            val pendingPublics = publicDeferreds.toMutableSet()
            var bestPublic: String? = null

            try {
                while (pendingLocals.isNotEmpty() || pendingPublics.isNotEmpty()) {
                    val outcome: Outcome = select {
                        pendingLocals.forEach { d ->
                            d.onAwait { result -> Outcome(d, result, isLocal = true) }
                        }
                        pendingPublics.forEach { d ->
                            d.onAwait { result -> Outcome(d, result, isLocal = false) }
                        }
                    }

                    if (outcome.isLocal) {
                        pendingLocals.remove(outcome.deferred)
                        if (outcome.result != null) {
                            // First LOCAL success wins.
                            (pendingLocals + pendingPublics).forEach { it.cancel() }
                            return@coroutineScope outcome.result
                        }
                    } else {
                        pendingPublics.remove(outcome.deferred)
                        if (bestPublic == null && outcome.result != null) {
                            bestPublic = outcome.result
                        }
                    }

                    if (pendingLocals.isEmpty() && bestPublic != null) {
                        // All locals done and we already have a public success: take it now.
                        pendingPublics.forEach { it.cancel() }
                        return@coroutineScope bestPublic
                    }
                }
                bestPublic
            } catch (t: Throwable) {
                (localDeferreds + publicDeferreds).forEach { it.cancel() }
                throw t
            }
        }
    }

    override suspend fun testSinglePath(baseUrl: String, isLocal: Boolean): String? {
        return probe(baseUrl, isLocal)
    }

    /**
     * Legacy sequential resolver. Kept so existing callers (e.g. login flow) continue to
     * work unchanged: LOCAL > PUBLIC > REMOTE, sequential, return the first reachable.
     */
    override suspend fun resolveAvailableBaseUrl(devicePaths: Map<DevicePathType, String>): String? {
        if (devicePaths.isEmpty()) return null
        for (priority in SEQUENTIAL_ORDER) {
            val baseUrl = devicePaths[priority] ?: continue
            val isLocal = priority == DevicePathType.LOCAL
            val result = probe(baseUrl, isLocal = isLocal)
            if (result != null) {
                Timber.d("DeviceUrlResolver(legacy): selected $baseUrl ($priority)")
                return result
            }
        }
        return null
    }

    private suspend fun probe(baseUrl: String, isLocal: Boolean): String? {
        // The verification endpoint expects the device root URL without the `/files`
        // suffix that we use for the base URL stored against accounts.
        val verificationUrl = baseUrl.removeSuffix("/files")
        Timber.d("DeviceUrlResolver: probing $verificationUrl (isLocal=$isLocal)")
        val ok = deviceVerificationClient.verifyDevice(verificationUrl, isLocal = isLocal)
        return if (ok) baseUrl else null
    }

    private data class Outcome(
        val deferred: Deferred<String?>,
        val result: String?,
        val isLocal: Boolean,
    )

    companion object {
        private val SEQUENTIAL_ORDER = listOf(
            DevicePathType.LOCAL,
            DevicePathType.PUBLIC,
            DevicePathType.REMOTE,
        )
    }
}
