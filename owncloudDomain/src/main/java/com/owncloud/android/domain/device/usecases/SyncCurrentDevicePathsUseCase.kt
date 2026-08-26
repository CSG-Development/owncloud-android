package com.owncloud.android.domain.device.usecases

import com.owncloud.android.domain.device.CurrentDeviceRepository
import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.domain.mdnsdiscovery.usecases.FindUpdatedAddressOfLocalDeviceUseCase
import com.owncloud.android.domain.remoteaccess.usecases.GetRemoteAccessTokenUseCase
import com.owncloud.android.domain.remoteaccess.usecases.GetRemoteAvailableDevicesUseCase
import timber.log.Timber

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
 * [skipMdns] skips Phase 1 so callers that only need Remote Access paths (e.g. share
 * link rewriting) still hit the API even when a LOCAL path is already cached.
 *
 * Returns true when at least one device source produced a usable result.
 */
class SyncCurrentDevicePathsUseCase(
    private val findUpdatedAddressOfLocalDeviceUseCase: FindUpdatedAddressOfLocalDeviceUseCase,
    private val getRemoteAvailableDevicesUseCase: GetRemoteAvailableDevicesUseCase,
    private val getRemoteAccessTokenUseCase: GetRemoteAccessTokenUseCase,
    private val saveCurrentDeviceUseCase: SaveCurrentDeviceUseCase,
    private val currentDeviceRepository: CurrentDeviceRepository,
) {

    suspend fun execute(
        wifiAvailable: Boolean = true,
        skipMdns: Boolean = false,
    ): Boolean {
        if (!getRemoteAccessTokenUseCase.hasToken()) {
            Timber.d("SyncCurrentDevicePaths: no Remote Access token, cannot sync")
            return false
        }
        Timber.d("SyncCurrentDevicePaths: syncing device paths from mDNS and Remote API")

        val savedDeviceCertCommonName = currentDeviceRepository.getSavedCertificateCommonName()
        val localDevice = if (!skipMdns && wifiAvailable) {
            findUpdatedAddressOfLocalDeviceUseCase.execute(savedDeviceCertCommonName)
        } else {
            val reason = if (skipMdns) "skipMdns=true" else "wifi unavailable"
            Timber.d("SyncCurrentDevicePaths: skipping mDNS discovery ($reason)")
            null
        }
        Timber.d("SyncCurrentDevicePaths: local mDNS device: $localDevice")

        val mergeKey = localDevice?.certificateCommonName?.takeIf { it.isNotEmpty() }
            ?: savedDeviceCertCommonName

        val remoteDevice = try {
            getRemoteAvailableDevicesUseCase.execute(filterByCertificateCommonName = mergeKey)
                .firstOrNull { it.certificateCommonName.isNotEmpty() && it.certificateCommonName == mergeKey }
        } catch (e: Exception) {
            Timber.w(e, "SyncCurrentDevicePaths: failed to fetch remote devices")
            null
        }
        Timber.d("SyncCurrentDevicePaths: remote device: $remoteDevice")

        val merged = mergeLocalAndRemoteDevices(localDevice, remoteDevice) ?: run {
            Timber.d("SyncCurrentDevicePaths: nothing to persist (no local or remote device)")
            return false
        }

        Timber.d("SyncCurrentDevicePaths: persisting merged device: $merged")
        saveCurrentDeviceUseCase(merged)
        return true
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
 * Exposed as a top-level function so it can be unit-tested without instantiating
 * [SyncCurrentDevicePathsUseCase].
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
