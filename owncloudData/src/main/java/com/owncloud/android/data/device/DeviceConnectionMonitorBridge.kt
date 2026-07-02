package com.owncloud.android.data.device

import com.owncloud.android.domain.device.DeviceConnectionMonitor

/**
 * Allows to notify the connection monitor
 * without a direct Koin dependency from the data module.
 */
object DeviceConnectionMonitorBridge {

    private var monitor: DeviceConnectionMonitor? = null

    fun install(monitor: DeviceConnectionMonitor) {
        this.monitor = monitor
    }

    fun reportUnreachable() {
        monitor?.reportUnreachable()
    }

    fun reportNoNetwork() {
        monitor?.reportNoNetwork()
    }

    fun reportConnected() {
        monitor?.reportConnected()
    }
}
