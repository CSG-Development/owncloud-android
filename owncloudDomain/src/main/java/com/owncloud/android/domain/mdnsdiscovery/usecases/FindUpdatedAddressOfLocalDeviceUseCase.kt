package com.owncloud.android.domain.mdnsdiscovery.usecases

import com.owncloud.android.domain.device.model.Device
import timber.log.Timber

class FindUpdatedAddressOfLocalDeviceUseCase(
    private val discoverLocalNetworkDevicesUseCase: DiscoverLocalNetworkDevicesUseCase,
) {

    suspend fun execute(savedDeviceCertCommonName: String?): Device? {
        Timber.d("FindUpdatedAddressOfLocalDeviceUseCase, savedDeviceCertCommonName: $savedDeviceCertCommonName")
        return try {
            discoverLocalNetworkDevicesUseCase.oneShot(DiscoverLocalNetworkDevicesUseCase.DEFAULT_MDNS_PARAMS).firstOrNull { foundDevice ->
                Timber.d("Found device via mDNS: $foundDevice")
                foundDevice.certificateCommonName == savedDeviceCertCommonName
            }
        } catch (e: Exception) {
            Timber.w(e, "mDNS discovery failed")
            null
        }
    }
}