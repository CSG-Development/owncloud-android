package com.owncloud.android.data.device

import com.owncloud.android.data.connectivity.Connectivity
import com.owncloud.android.data.connectivity.NetworkStateObserver
import com.owncloud.android.domain.device.NetworkConnectivityGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class NetworkConnectivityGatewayImpl(
    networkStateObserver: NetworkStateObserver,
) : NetworkConnectivityGateway {

    @Volatile
    private var lastConnectivity: Connectivity = Connectivity.unavailable()

    private val connectivityFlow: Flow<Connectivity> = networkStateObserver.observeNetworkState()
        .onEach { lastConnectivity = it }

    override fun hasNetwork(): Boolean = lastConnectivity.hasAnyNetwork()

    override fun allowsLocalPathProbe(): Boolean = lastConnectivity.allowsLocalPathProbe()

    override fun observe(): Flow<Unit> = connectivityFlow.map { }
}
