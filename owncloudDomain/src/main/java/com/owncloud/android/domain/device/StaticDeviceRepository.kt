package com.owncloud.android.domain.device

import com.owncloud.android.domain.device.model.Device
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing static device configuration.
 * Static device is a manually configured device with only REMOTE path available.
 */
interface StaticDeviceRepository {

    /**
     * Save static device.
     * Only the REMOTE path URL will be stored.
     * @param device Device to save (only REMOTE path will be extracted)
     */
    fun saveStaticDeviceUrl(remoteUrl: String)


    /**
     * Get the current static device as a Flow.
     * Emits null if no static device is configured.
     * Emits new Device whenever static device is updated.
     */
    fun getStaticDeviceAsFlow(): Flow<Device?>

    /**
     * Get the current static device.
     * @return Device with only REMOTE path, or null if not configured
     */
    fun getStaticDevice(): Device?

    /**
     * Clear the static device configuration.
     */
    fun clearStaticDevice()
}
