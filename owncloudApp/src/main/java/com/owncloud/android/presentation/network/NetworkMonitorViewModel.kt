package com.owncloud.android.presentation.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.domain.device.DeviceConnectionMonitor
import com.owncloud.android.domain.device.DeviceConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class NetworkMonitorViewModel(
    private val deviceConnectionMonitor: DeviceConnectionMonitor,
) : ViewModel() {

    val connectionState: Flow<DeviceConnectionState> = deviceConnectionMonitor.state
        .scan(DeviceConnectionState.Connected) { previous: DeviceConnectionState, new ->
            // Keep ConnectionLost until it's restored or Retry clicked
            val newAdjusted = when (previous) {
                DeviceConnectionState.Connected -> new
                DeviceConnectionState.ConnectionLost -> {
                    if (new is DeviceConnectionState.FindingNetwork && !new.isForced) {
                        previous
                    } else {
                        new
                    }
                }
                is DeviceConnectionState.FindingNetwork -> {
                    if (new is DeviceConnectionState.FindingNetwork) {
                        new.copy(isForced = new.isForced || previous.isForced)
                    } else {
                        new
                    }
                }
                DeviceConnectionState.NoInternet -> new
            }
            Timber.d("UI connection state raw: $new, adjusted: $newAdjusted")
            return@scan newAdjusted
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeviceConnectionState.Connected,
        )

    fun onRetryClicked() {
        viewModelScope.launch {
            deviceConnectionMonitor.retryConnection()
        }
    }
}
