package com.owncloud.android.data.connectivity

import android.net.NetworkRequest

internal class NetworkRequestFactory {

    fun createNetworkRequest(vararg capability: Int): NetworkRequest =
        NetworkRequest.Builder().apply {
            capability.forEach {
                addCapability(it)
            }
        }.build()
}