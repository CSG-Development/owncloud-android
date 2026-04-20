package com.owncloud.android.data.connectivity

import android.net.NetworkCapabilities

data class Connectivity(
    val type: Set<ConnectionType> = setOf(ConnectionType.NONE),
) {

    fun hasAnyNetwork(): Boolean =
        type.any { it != ConnectionType.NONE }

    /**
     * Returns true when the connectivity is likely to provide LAN access (WiFi, Ethernet or VPN).
     * Cellular-only connections are not considered LAN-capable.
     *
     * Used to gate local mDNS discovery and LOCAL path probing. When the active network is
     * cellular-only there is no realistic chance to reach the local device, so we skip the
     * (potentially very slow) local checks. Unknown / empty state is intentionally NOT treated
     * as LAN-capable here, callers should use [isWifiStateUnknown] and decide whether to try
     * anyway.
     */
    fun isLanLikely(): Boolean =
        type.any {
            it == ConnectionType.WIFI ||
                it == ConnectionType.ETHERNET ||
                it == ConnectionType.VPN
        }

    /**
     * Returns true when we have no information about the connectivity state. In this case
     * callers should optimistically attempt local discovery rather than skipping it (per
     * reference algorithm rule: "if the connectivity API itself fails, the implementation
     * should prefer trying local discovery anyway").
     */
    fun isWifiStateUnknown(): Boolean = type.isEmpty() || type == setOf(ConnectionType.NONE)

    enum class ConnectionType {
        NONE,
        WIFI,
        CELLULAR,
        ETHERNET,
        VPN,
    }

    companion object {

        fun fromNetworkCapabilities(networkCapabilities: NetworkCapabilities): Connectivity {
            val networkTypes = mutableSetOf<ConnectionType>()
            addCapabilities(networkCapabilities, networkTypes)
            networkTypes.takeIf { it.isEmpty() }?.run { networkTypes.add(ConnectionType.NONE) }
            return Connectivity(networkTypes)
        }

        fun fromNetworkCapabilities(networkCapabilities: List<NetworkCapabilities>): Connectivity {
            val networkTypes = mutableSetOf<ConnectionType>()
            for (networkCapability in networkCapabilities) {
                addCapabilities(networkCapability, networkTypes)
            }
            networkTypes.takeIf { it.isEmpty() }?.run { networkTypes.add(ConnectionType.NONE) }
            return Connectivity(networkTypes)
        }

        private fun addCapabilities(networkCapabilities: NetworkCapabilities, networkTypes: MutableSet<ConnectionType>) {
            networkCapabilities.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) }?.run { networkTypes.add(ConnectionType.CELLULAR) }
            networkCapabilities.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }?.run { networkTypes.add(ConnectionType.VPN) }
            networkCapabilities.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }?.run { networkTypes.add(ConnectionType.WIFI) }
            networkCapabilities.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) }?.run { networkTypes.add(ConnectionType.ETHERNET) }
        }

        fun unavailable(): Connectivity = Connectivity()

    }
}