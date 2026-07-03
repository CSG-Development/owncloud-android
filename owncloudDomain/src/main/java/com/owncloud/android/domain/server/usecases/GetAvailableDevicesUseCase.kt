package com.owncloud.android.domain.server.usecases

import com.owncloud.android.domain.device.StaticDeviceRepository
import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.domain.device.usecases.GetSavedDeviceCertificateUseCase
import com.owncloud.android.domain.mdnsdiscovery.usecases.DiscoverLocalNetworkDevicesUseCase
import com.owncloud.android.domain.remoteaccess.usecases.GetRemoteAvailableDevicesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import timber.log.Timber

class GetAvailableDevicesUseCase(
    private val getRemoteAvailableDevicesUseCase: GetRemoteAvailableDevicesUseCase,
    private val discoverLocalNetworkDevicesUseCase: DiscoverLocalNetworkDevicesUseCase,
    private val getSavedDeviceCertificateUseCase: GetSavedDeviceCertificateUseCase,
    private val staticDeviceRepository: StaticDeviceRepository,
) {

    companion object {
        private const val NO_EXIST_INDEX = -1
    }

    private val remoteAccessDevicesFlow = MutableStateFlow(emptyList<Device>())

    suspend fun refreshRemoteAccessDevices() {
        val remoteAccessDevices = getRemoteAvailableDevicesUseCase.execute()
        remoteAccessDevicesFlow.update { remoteAccessDevices }
    }

    fun getServersUpdates(
        scope: CoroutineScope,
        discoverLocalNetworkDevicesParams: DiscoverLocalNetworkDevicesUseCase.Params
    ): StateFlow<List<Device>> {
        remoteAccessDevicesFlow.update { emptyList() }
        val localNetworkDevicesFlow = discoverLocalNetworkDevicesUseCase.execute(discoverLocalNetworkDevicesParams)
            .scan(emptyList<Device>()) { devicesList, newDevice ->
                (devicesList + newDevice).distinctBy { it.certificateCommonName }
            }

        return combine(
            remoteAccessDevicesFlow,
            localNetworkDevicesFlow,
            staticDeviceRepository.getStaticDeviceAsFlow()
        ) { remoteDevices, localDevices, staticDevice ->
            Timber.d("Remote access devices: $remoteDevices, Local devices: $localDevices")

            val mergedDeviceList = remoteDevices.mergeWith(localDevices)
                .sortDevicesByPriority()
            val finalList = staticDevice?.let { mergedDeviceList.toMutableList().apply { add(0, it) } } ?: mergedDeviceList
            finalList
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    }

    private fun List<Device>.sortDevicesByPriority(): List<Device> {
        val devices = this.toMutableList()
        val savedCertificate = getSavedDeviceCertificateUseCase()
        if (savedCertificate.isNullOrEmpty()) {
            return devices.toList()
        }

        val priorityDeviceIndex = devices.indexOfFirst { device ->
            device.certificateCommonName == savedCertificate
        }

        if (priorityDeviceIndex != NO_EXIST_INDEX && priorityDeviceIndex != 0) {
            val priorityDevice = devices.removeAt(priorityDeviceIndex)
            devices.add(0, priorityDevice)
        }

        return devices.toList()
    }

    private fun List<Device>.mergeWith(anotherList: List<Device>): List<Device> {
        val mutableDevices = this.toMutableList()

        // If we have a local network discovery server, try to merge it with existing devices
        anotherList.forEach { localDevice ->
            val localCertificate = localDevice.certificateCommonName

            // Try to find an existing device with the same certificate
            val existingDeviceIndex = if (localCertificate.isNotEmpty()) {
                mutableDevices.indexOfFirst { device ->
                    device.certificateCommonName == localCertificate
                }
            } else {
                NO_EXIST_INDEX
            }

            if (existingDeviceIndex != NO_EXIST_INDEX) {
                val existingDevice = mutableDevices[existingDeviceIndex]
                val updatedPaths = existingDevice.availablePaths.toMutableMap()

                if (!updatedPaths.containsKey(DevicePathType.LOCAL)) {
                    val localDevicePath = localDevice.availablePaths[DevicePathType.LOCAL]
                    if (localDevicePath != null) {
                        updatedPaths[DevicePathType.LOCAL] = localDevicePath

                        mutableDevices[existingDeviceIndex] = Device(
                            id = existingDevice.id,
                            name = localDevice.name,
                            availablePaths = updatedPaths,
                            certificateCommonName = existingDevice.certificateCommonName
                        )
                    }
                }
            } else {
                mutableDevices.add(localDevice)
            }
        }
        return mutableDevices.toList()
    }
}