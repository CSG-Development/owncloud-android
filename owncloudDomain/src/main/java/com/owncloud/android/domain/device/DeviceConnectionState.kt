package com.owncloud.android.domain.device

sealed class DeviceConnectionState {
    data object Connected : DeviceConnectionState()
    data object NoInternet : DeviceConnectionState()
    data class FindingNetwork(val isForced: Boolean) : DeviceConnectionState()
    data object ConnectionLost : DeviceConnectionState()
}
