package com.owncloud.android.domain.device.model

data class Device(
    val id: String,
    val name: String,
    val availablePaths: Map<DevicePathType, DevicePath>,
    val certificateCommonName: String = "",
)

