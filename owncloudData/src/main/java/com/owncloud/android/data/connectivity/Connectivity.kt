package com.owncloud.android.data.connectivity

import android.net.NetworkCapabilities

data class Connectivity(
    val type: Set<ConnectionType> = setOf(ConnectionType.NONE),
) {

    fun hasAnyNetwork(): Boolean =
        type.any { it != ConnectionType.NONE }

    enum class ConnectionType {
        NONE,
        WIFI,
        CELLULAR,
        ETHERNET
    }

    companion object {

        fun fromNetworkCapabilities(networkCapabilities: NetworkCapabilities): Connectivity {
            val networkTypes = mutableSetOf<ConnectionType>()
            networkCapabilities.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) }?.run { networkTypes.add(ConnectionType.CELLULAR) }
            networkCapabilities.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }?.run { networkTypes.add(ConnectionType.WIFI) }
            networkCapabilities.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) }?.run { networkTypes.add(ConnectionType.ETHERNET) }
            networkTypes.takeIf { it.isEmpty() }?.run { networkTypes.add(ConnectionType.NONE) }
            return Connectivity(networkTypes)
        }

        fun unavailable(): Connectivity = Connectivity()

    }
}