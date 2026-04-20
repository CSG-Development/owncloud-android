package com.owncloud.android.domain.device

/**
 * Chooses the best available device base URL.
 *
 * Implementation contract (reference Algorithm B):
 *  1. Test cached priority paths (LOCAL + PUBLIC) in parallel.
 *  2. If they all fail and the cache is expired, fetch fresh paths from the Remote Access
 *     backend; if they differ from the cached set, replace the cache and re-test once;
 *     if they are identical, only refresh the cache timestamp (do not re-probe).
 *  3. As a last resort, test the REMOTE relay path.
 *  4. Return the first reachable URL or `null` if none are reachable.
 *
 * @param wifiAvailable When false, LOCAL paths are skipped (cellular-only network).
 */
interface BaseUrlChooser {

    suspend fun chooseBestAvailableBaseUrl(wifiAvailable: Boolean = true): String?
}
