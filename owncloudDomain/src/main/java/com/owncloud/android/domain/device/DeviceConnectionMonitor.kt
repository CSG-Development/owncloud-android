package com.owncloud.android.domain.device

import kotlinx.coroutines.flow.Flow

interface DeviceConnectionMonitor {

    val state: Flow<DeviceConnectionState>

    fun start(fromBackground: Boolean)

    fun stop()

    suspend fun evaluateConnection()

    suspend fun retryConnection()

    fun reportUnreachable()

    fun reportNoNetwork()

    fun reportConnected()
}
