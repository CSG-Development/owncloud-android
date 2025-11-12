package com.owncloud.android.domain.device.model

/**
 * Represents a device with multiple access paths
 *
 * @property availablePaths Map of all available paths to access this device
 * @property preferredPath The preferred path to use for accessing this device
 */
data class Device(
    val id: String,
    val availablePaths: Map<DevicePathType, DevicePath>,
    val preferredPath: DevicePath,
) {
    /**
     * Get the device name from the preferred path
     */
    val name: String
        get() = preferredPath.hostName

    /**
     * Get the certificate common name from the preferred path
     */
    val certificateCommonName: String
        get() = preferredPath.certificateCommonName
}

