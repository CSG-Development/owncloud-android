package com.owncloud.android.domain.device.model

/**
 * Represents a device with multiple access paths
 *
 * @property id Unique device identifier
 * @property name Device name (friendly name)
 * @property availablePaths Map of all available paths to access this device
 * @property certificateCommonName SSL certificate common name for device verification
 */
data class Device(
    val id: String,
    val name: String,
    val availablePaths: Map<DevicePathType, DevicePath>,
    val certificateCommonName: String = "",
)

