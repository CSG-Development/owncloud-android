package com.owncloud.android.data.device

import com.owncloud.android.domain.device.StaticDeviceRepository
import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.domain.device.model.DevicePathType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Repository implementation for managing static device configuration.
 * Accepts Device object and extracts REMOTE path URL to store in DeveloperOptionsStorage.
 */
class HCStaticDeviceRepository(
    private val developerOptionsStorage: DeveloperOptionsStorage
) : StaticDeviceRepository {

    private val _staticDeviceUrlFlow = MutableStateFlow(developerOptionsStorage.getStaticDeviceUrl())

    override fun saveStaticDevice(device: Device) {
        val remoteUrl = device.availablePaths[DevicePathType.REMOTE]
        if (remoteUrl != null) {
            developerOptionsStorage.saveStaticDeviceUrl(remoteUrl)
            _staticDeviceUrlFlow.update { remoteUrl }
        } else {
            // If no REMOTE path, try to get any available path
            val anyUrl = device.availablePaths.values.firstOrNull()
            if (anyUrl != null) {
                developerOptionsStorage.saveStaticDeviceUrl(anyUrl)
                _staticDeviceUrlFlow.update { anyUrl }
            }
        }
    }

    override fun getStaticDeviceAsFlow(): Flow<Device?> {
        return _staticDeviceUrlFlow.asStateFlow().map { url ->
            url?.takeIf { it.isNotEmpty() }?.let { createStaticDevice(it) }
        }
    }

    override fun getStaticDevice(): Device? {
        val url = developerOptionsStorage.getStaticDeviceUrl()
        return url?.takeIf { it.isNotEmpty() }?.let { createStaticDevice(it) }
    }

    override fun clearStaticDevice() {
        developerOptionsStorage.clearStaticDeviceUrl()
        _staticDeviceUrlFlow.update { null }
    }

    private fun createStaticDevice(remoteUrl: String): Device {
        return Device(
            id = STATIC_DEVICE_ID,
            name = remoteUrl,
            availablePaths = mapOf(DevicePathType.REMOTE to remoteUrl),
            certificateCommonName = ""
        )
    }

    companion object {
        private const val STATIC_DEVICE_ID = "static_device"
    }
}
