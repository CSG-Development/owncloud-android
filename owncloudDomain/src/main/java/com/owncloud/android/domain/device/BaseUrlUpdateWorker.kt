package com.owncloud.android.domain.device

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.domain.device.usecases.SaveCurrentDeviceUseCase
import com.owncloud.android.domain.device.usecases.SwitchToBestAvailableBaseUrlUseCase
import com.owncloud.android.domain.mdnsdiscovery.usecases.DiscoverLocalNetworkDevicesUseCase
import com.owncloud.android.domain.remoteaccess.usecases.GetRemoteAccessTokenUseCase
import com.owncloud.android.domain.remoteaccess.usecases.GetRemoteAvailableDevicesUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.IOException

/**
 * Worker responsible for updating the base URL.
 *
 * Aligned with reference Algorithms A and B:
 *  1. Try [SwitchToBestAvailableBaseUrlUseCase] first. The chooser is responsible for
 *     reading the cached paths, refreshing them via the Remote Access backend when the
 *     cache is expired (cheap fast-path: when [seagateDeviceId] is known we never need to
 *     re-run mDNS for paths only) and falling back to the relay path.
 *  2. If no cached paths exist or the chooser cannot find a reachable URL, run a full
 *     discovery cycle: mDNS + Remote Access enumeration, MERGE the results by
 *     `certificateCommonName` (Phase 1 + Phase 2), persist the merged device (including
 *     the seagateDeviceID and a fresh cache timestamp) and try the chooser one more time.
 */
class BaseUrlUpdateWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters), KoinComponent {

    private val discoverLocalNetworkDevicesUseCase: DiscoverLocalNetworkDevicesUseCase by inject()
    private val getRemoteAvailableDevicesUseCase: GetRemoteAvailableDevicesUseCase by inject()

    private val getRemoteAccessTokenUseCase: GetRemoteAccessTokenUseCase by inject()
    private val saveCurrentDeviceUseCase: SaveCurrentDeviceUseCase by inject()
    private val accountBaseUrlManager: AccountBaseUrlManager by inject()
    private val currentDeviceRepository: CurrentDeviceRepository by inject()

    private val switchToBestAvailableBaseUrlUseCase: SwitchToBestAvailableBaseUrlUseCase by inject()

    override suspend fun doWork(): Result {
        return try {
            val fromBackground = inputData.getBoolean(KEY_FROM_BACKGROUND, false)
            val wifiAvailable = inputData.getBoolean(KEY_WIFI_AVAILABLE, true)
            setProgress(workDataOf(KEY_FROM_BACKGROUND to fromBackground))

            Timber.d(
                "BaseUrlUpdateWorker: starting (fromBackground=$fromBackground, wifiAvailable=$wifiAvailable)"
            )
            if (!accountBaseUrlManager.hasActiveAccount()) {
                Timber.d("BaseUrlUpdateWorker: no active account, skipping")
                return Result.success()
            }

            // Step 1: try chooser with whatever is cached (also handles fast-path refresh).
            val updatedFromCurrentPaths = switchToBestAvailableBaseUrlUseCase.execute(wifiAvailable)
            if (updatedFromCurrentPaths) {
                Timber.d("BaseUrlUpdateWorker: base URL updated from cached/refreshed paths")
                return Result.success()
            }

            // Step 2: full discovery cycle (mDNS Phase 1 + Remote Phase 2 merged).
            Timber.d("BaseUrlUpdateWorker: cached paths failed, running full discovery")
            if (syncDevicePaths(wifiAvailable)) {
                switchToBestAvailableBaseUrlUseCase.execute(wifiAvailable)
            }
            Timber.d("BaseUrlUpdateWorker: completed")
            Result.success()
        } catch (e: IOException) {
            Timber.e(e, "BaseUrlUpdateWorker: failed with IO error - ${e.message}")
            Result.retry()
        } catch (e: Exception) {
            Timber.e(e, "BaseUrlUpdateWorker: failed - ${e.message}")
            Result.failure()
        }
    }

    /**
     * Discover the current device via mDNS (Phase 1) and via the Remote Access backend
     * (Phase 2) and merge the results by `certificateCommonName`. The merged device is
     * persisted (paths + certificateCommonName + seagateDeviceID + cache timestamp).
     *
     * Behavior matrix:
     *  - mDNS only           ⇒ persist with LOCAL path; no seagateDeviceID, no PUBLIC/REMOTE.
     *  - Remote only         ⇒ persist remote device as-is.
     *  - mDNS + Remote (same `certificateCommonName`) ⇒ persist merged device:
     *      LOCAL from mDNS, PUBLIC/REMOTE/seagateDeviceID/friendlyName from Remote.
     *  - Remote without matching mDNS ⇒ persist remote device (no LOCAL path).
     *
     * Returns true when at least one device source produced a usable result.
     */
    private suspend fun syncDevicePaths(wifiAvailable: Boolean): Boolean {
        if (!getRemoteAccessTokenUseCase.hasToken()) {
            Timber.d("BaseUrlUpdateWorker: no Remote Access token, cannot sync")
            return false
        }
        Timber.d("BaseUrlUpdateWorker: syncing device paths from mDNS and Remote API")

        val localDevice = if (wifiAvailable) {
            try {
                discoverLocalNetworkDevicesUseCase.oneShot(DiscoverLocalNetworkDevicesUseCase.DEFAULT_MDNS_PARAMS)
            } catch (e: Exception) {
                Timber.w(e, "BaseUrlUpdateWorker: mDNS discovery failed")
                null
            }
        } else {
            Timber.d("BaseUrlUpdateWorker: skipping mDNS discovery (wifi unavailable)")
            null
        }
        Timber.d("BaseUrlUpdateWorker: local mDNS device: $localDevice")

        val savedCertCommonName = currentDeviceRepository.getSavedCertificateCommonName()
        val mergeKey = localDevice?.certificateCommonName?.takeIf { it.isNotEmpty() }
            ?: savedCertCommonName

        // Always try to enrich with remote data (relay/public/seagateDeviceID).
        val remoteDevice = try {
            val all = getRemoteAvailableDevicesUseCase.execute()
            // Prefer the entry that matches our merge key; fall back to currentDevice() helper.
            all.firstOrNull { it.certificateCommonName.isNotEmpty() && it.certificateCommonName == mergeKey }
                ?: getRemoteAvailableDevicesUseCase.currentDevice()
        } catch (e: Exception) {
            Timber.w(e, "BaseUrlUpdateWorker: failed to fetch remote devices")
            null
        }
        Timber.d("BaseUrlUpdateWorker: remote device: $remoteDevice")

        val merged = mergeDevices(localDevice, remoteDevice) ?: run {
            Timber.d("BaseUrlUpdateWorker: nothing to persist (no local or remote device)")
            return false
        }

        Timber.d("BaseUrlUpdateWorker: persisting merged device: $merged")
        saveCurrentDeviceUseCase(merged)
        return true
    }

    private fun mergeDevices(local: Device?, remote: Device?): Device? =
        mergeLocalAndRemoteDevices(local, remote)

    companion object {
        const val BASE_URL_UPDATE_WORKER = "BASE_URL_UPDATE_WORKER"
        const val KEY_FROM_BACKGROUND = "KEY_FROM_BACKGROUND"
        const val KEY_WIFI_AVAILABLE = "KEY_WIFI_AVAILABLE"
    }
}

/**
 * Merge a local mDNS device with a remote device by [Device.certificateCommonName].
 *
 * - When both are present and certificate names match, take LOCAL from mDNS and the
 *   remaining metadata (PUBLIC/REMOTE paths, seagateDeviceID via Device.id,
 *   friendlyName) from the remote entry.
 * - When only one source is present, return it unchanged.
 * - When both are present but certificate names differ, prefer the remote entry (it is
 *   the source of truth for the authenticated user) but still keep the LOCAL path
 *   from mDNS in case it happens to be reachable.
 *
 * Exposed as a top-level function (kept package-private behaviorally) so it can be
 * unit-tested without instantiating the Android [BaseUrlUpdateWorker].
 */
internal fun mergeLocalAndRemoteDevices(local: Device?, remote: Device?): Device? {
    if (local == null && remote == null) return null
    if (remote == null) return local
    if (local == null) return remote

    val mergedPaths = mutableMapOf<DevicePathType, String>()
    mergedPaths.putAll(remote.availablePaths)
    local.availablePaths[DevicePathType.LOCAL]?.let { mergedPaths[DevicePathType.LOCAL] = it }

    val certificateCommonName = when {
        remote.certificateCommonName.isNotEmpty() -> remote.certificateCommonName
        else -> local.certificateCommonName
    }
    return Device(
        id = remote.id,
        name = remote.name,
        availablePaths = mergedPaths,
        certificateCommonName = certificateCommonName,
    )
}
