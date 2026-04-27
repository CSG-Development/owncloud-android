package com.owncloud.android.domain.device

import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.domain.device.model.DevicePathType

interface CurrentDeviceRepository {
    /**
     * Persist the current device including its paths, certificate common name and Remote
     * Access seagateDeviceID. Stamps the cache timestamp.
     */
    fun saveCurrentDevice(device: Device)

    fun getCurrentDevicePaths(): Map<DevicePathType, String>

    fun getSavedCertificateCommonName(): String?

    /**
     * Returns the persisted Remote-Access device id (if any). Used by the network-change
     * fast-path to request fresh paths without re-running mDNS discovery.
     */
    fun getSeagateDeviceId(): String?

    /**
     * Returns true when the cached paths are older than the TTL (1 hour by default) or no
     * timestamp is recorded.
     */
    fun arePathsExpired(): Boolean

    fun clearCurrentDevicePaths()
}
