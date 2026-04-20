package com.owncloud.android.data.device

import com.owncloud.android.domain.device.BaseUrlChooser
import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.domain.remoteaccess.RemoteAccessRepository
import com.owncloud.android.domain.server.usecases.DeviceUrlResolver
import timber.log.Timber

/**
 * Implements [BaseUrlChooser] following reference Algorithm B.
 *
 * Resolution order:
 *  1. Read the cached priority paths from [CurrentDeviceStorage]; probe LOCAL+PUBLIC in
 *     parallel via [DeviceUrlResolver.testPriorityPaths].
 *  2. If those fail and the cache is expired AND a [seagateDeviceId] is known, fetch
 *     fresh paths from the Remote Access backend.
 *      - If the fresh paths equal the cached ones, only refresh the timestamp and stop
 *        (avoid pointless re-probing of the same failing endpoints).
 *      - Otherwise, replace the cached paths and re-test priority paths once.
 *  3. As a last resort, probe the REMOTE relay path.
 */
class HCBaseUrlChooser(
    private val currentDeviceStorage: CurrentDeviceStorage,
    private val deviceUrlResolver: DeviceUrlResolver,
    private val remoteAccessRepository: RemoteAccessRepository,
) : BaseUrlChooser {

    override suspend fun chooseBestAvailableBaseUrl(wifiAvailable: Boolean): String? {
        val cachedPaths = readCachedPaths()
        if (cachedPaths.isEmpty()) {
            Timber.d("BaseUrlChooser: no cached paths")
            return null
        }

        Timber.d("BaseUrlChooser: probing cached priority paths (wifiAvailable=$wifiAvailable)")
        val priority = deviceUrlResolver.testPriorityPaths(cachedPaths, wifiAvailable)
        if (priority != null) {
            Timber.d("BaseUrlChooser: priority path succeeded ($priority)")
            return priority
        }

        // Cache-refresh step: only when the cache is expired and we know the device id.
        if (currentDeviceStorage.arePathsExpired()) {
            val seagateDeviceId = currentDeviceStorage.getSeagateDeviceId()
            if (!seagateDeviceId.isNullOrEmpty() && remoteAccessRepository.hasAccessToken()) {
                val freshPaths = remoteAccessRepository.getDevicePathsById(seagateDeviceId)
                if (freshPaths != null) {
                    val cachedPriority = cachedPaths.filterKeys { it != DevicePathType.REMOTE }
                    val freshPriority = freshPaths.filterKeys { it != DevicePathType.REMOTE }
                    if (freshPaths == cachedPaths) {
                        // Identical: refresh the cache timestamp and stop. Avoids probing
                        // the same failing paths a second time.
                        Timber.d("BaseUrlChooser: refreshed paths identical, updating timestamp only")
                        currentDeviceStorage.savePathsTimestamp()
                    } else {
                        Timber.d("BaseUrlChooser: refreshed paths differ, replacing cache and re-probing")
                        currentDeviceStorage.replacePaths(freshPaths)
                        if (freshPriority != cachedPriority) {
                            val freshResult = deviceUrlResolver.testPriorityPaths(freshPaths, wifiAvailable)
                            if (freshResult != null) {
                                Timber.d("BaseUrlChooser: fresh priority path succeeded ($freshResult)")
                                return freshResult
                            }
                        }
                    }
                }
            }
        }

        // Relay fallback (always last). Use the most up-to-date relay path from storage so
        // a refreshed cache (above) is also reflected here.
        val relayUrl = currentDeviceStorage.getDeviceBaseUrl(DevicePathType.REMOTE.name)
        if (relayUrl != null) {
            Timber.d("BaseUrlChooser: trying remote relay fallback ($relayUrl)")
            val ok = deviceUrlResolver.testSinglePath(relayUrl, isLocal = false)
            if (ok != null) {
                Timber.d("BaseUrlChooser: relay path succeeded ($ok)")
                return ok
            }
        }

        Timber.d("BaseUrlChooser: no reachable base URL")
        return null
    }

    private fun readCachedPaths(): Map<DevicePathType, String> {
        val map = mutableMapOf<DevicePathType, String>()
        DevicePathType.entries.forEach { type ->
            currentDeviceStorage.getDeviceBaseUrl(type.name)?.let { map[type] = it }
        }
        return map
    }
}
