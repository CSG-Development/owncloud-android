package com.owncloud.android.domain.server.usecases

import com.owncloud.android.domain.device.model.DevicePathType

/**
 * Resolves the best available device base URL by probing candidate paths.
 *
 * Aligned with reference Algorithm C ("test priority paths"):
 *  - LOCAL and PUBLIC paths are probed in parallel.
 *  - The first LOCAL success is returned immediately (other probes are cancelled).
 *  - If all LOCAL probes fail, the first PUBLIC success is returned once every LOCAL has
 *    completed (so a fast public response cannot beat a slower successful local one).
 *  - REMOTE paths are NEVER tested by this resolver: relay is a last-resort fallback owned
 *    by the higher-level [com.owncloud.android.domain.device.BaseUrlChooser] orchestrator.
 *  - When [wifiAvailable] is false the LOCAL paths are skipped entirely (cellular-only
 *    networks cannot reach the device on the LAN).
 */
interface DeviceUrlResolver {

    /**
     * Test priority paths (LOCAL + PUBLIC) in parallel. See class KDoc for the full
     * selection rules.
     *
     * Backwards-compat: the legacy [resolveAvailableBaseUrl] entry point still exists for
     * callers that haven't been migrated yet (e.g. login flow); it delegates to the new
     * implementation while preserving the old sequential semantics.
     *
     * @param paths Map of [DevicePathType] to base URL (may include REMOTE; that entry is
     *  ignored by [testPriorityPaths]).
     * @param wifiAvailable Whether LOCAL paths should be considered.
     * @return The first available base URL or `null` when none of the priority paths
     *  responded successfully.
     */
    suspend fun testPriorityPaths(
        paths: Map<DevicePathType, String>,
        wifiAvailable: Boolean,
    ): String?

    /**
     * Test a single base URL. Used for the relay/REMOTE fallback step.
     *
     * @param baseUrl The candidate base URL (the optional trailing `/files` segment is
     *  stripped before probing).
     * @param isLocal Whether to apply the local timeout (4s) or the non-local timeout (9s).
     * @return The same [baseUrl] when reachable, `null` otherwise.
     */
    suspend fun testSinglePath(baseUrl: String, isLocal: Boolean): String?

    /**
     * Legacy sequential resolver kept for callers that still expect strict LOCAL >
     * PUBLIC > REMOTE ordering (e.g. login flow). New callers should prefer
     * [testPriorityPaths] + an explicit relay fallback.
     */
    suspend fun resolveAvailableBaseUrl(devicePaths: Map<DevicePathType, String>): String?
}
