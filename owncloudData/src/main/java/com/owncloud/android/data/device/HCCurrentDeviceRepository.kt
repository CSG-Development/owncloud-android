package com.owncloud.android.data.device

import com.owncloud.android.domain.device.CurrentDeviceRepository
import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.lib.common.accounts.AccountDataStorage

class HCCurrentDeviceRepository(
    private val currentDeviceStorage: CurrentDeviceStorage,
    private val accountDataStorage: AccountDataStorage,
) : CurrentDeviceRepository {

    override fun saveCurrentDevice(device: Device) {
        // Replace paths atomically and stamp the cache timestamp so the TTL window starts
        // at the moment we saved fresh paths.
        currentDeviceStorage.replacePaths(device.availablePaths)
        currentDeviceStorage.saveCertificateCommonName(device.certificateCommonName)
        accountDataStorage.saveDeviceCertCommonName(device.certificateCommonName)
        if (device.id.isNotEmpty()) {
            currentDeviceStorage.saveSeagateDeviceId(device.id)
        }
    }

    override fun getCurrentDevicePaths(): Map<DevicePathType, String> {
        val paths = mutableMapOf<DevicePathType, String>()
        DevicePathType.entries.forEach {
            currentDeviceStorage.getDeviceBaseUrl(it.name)?.let { url ->
                paths[it] = url
            }
        }
        return paths
    }

    override fun getSavedCertificateCommonName(): String? {
        return currentDeviceStorage.getCertificateCommonName()
    }

    override fun getSeagateDeviceId(): String? = currentDeviceStorage.getSeagateDeviceId()

    override fun arePathsExpired(): Boolean = currentDeviceStorage.arePathsExpired()

    override fun clearCurrentDevicePaths() {
        currentDeviceStorage.clearDevicePaths()
    }
}
