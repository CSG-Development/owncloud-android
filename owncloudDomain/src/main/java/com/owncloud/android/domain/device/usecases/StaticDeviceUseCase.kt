package com.owncloud.android.domain.device.usecases

import com.owncloud.android.domain.device.StaticDeviceRepository
import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.domain.device.model.DevicePathType
import kotlinx.coroutines.flow.Flow

/**
 * Use case for saving and observing static device configuration.
 * Static device is a manually configured device with only REMOTE path available.
 */
class StaticDeviceUseCase(
    private val staticDeviceRepository: StaticDeviceRepository
) {

    /**
     * Save static device from URL.
     * Creates a Device with only REMOTE path available.
     * @param remoteUrl The remote URL for the static device
     */
    fun execute(remoteUrl: String) {
        if (remoteUrl.isBlank()) {
            staticDeviceRepository.clearStaticDevice()
            return
        }

        val device = Device(
            id = STATIC_DEVICE_ID,
            name = remoteUrl,
            availablePaths = mapOf(DevicePathType.REMOTE to remoteUrl),
            certificateCommonName = ""
        )
        staticDeviceRepository.saveStaticDevice(device)
    }

    /**
     * Save static device.
     * Only the REMOTE path from the device will be stored.
     * @param device Device to save
     */
    fun execute(device: Device) {
        staticDeviceRepository.saveStaticDevice(device)
    }

    /**
     * Get static device as Flow.
     * Emits Device with only REMOTE path whenever it changes.
     * Emits null if no static device is configured.
     */
    fun getStaticDeviceFlow(): Flow<Device?> {
        return staticDeviceRepository.getStaticDeviceAsFlow()
    }

    /**
     * Get current static device.
     * @return Device with only REMOTE path, or null if not configured
     */
    fun getStaticDevice(): Device? {
        return staticDeviceRepository.getStaticDevice()
    }

    /**
     * Clear static device configuration.
     */
    fun clear() {
        staticDeviceRepository.clearStaticDevice()
    }

    companion object Companion {
        const val STATIC_DEVICE_ID = "static_device"
    }
}
