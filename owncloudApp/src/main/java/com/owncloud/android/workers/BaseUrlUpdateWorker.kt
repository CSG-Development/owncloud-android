package com.owncloud.android.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.owncloud.android.domain.device.SaveCurrentDeviceUseCase
import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.domain.device.usecases.DynamicUrlSwitchingController
import com.owncloud.android.domain.mdnsdiscovery.usecases.DiscoverLocalNetworkDevicesUseCase
import com.owncloud.android.domain.remoteaccess.usecases.GetRemoteAvailableDevicesUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * Worker responsible for updating base URLs by combining mDNS discovery
 * and remote access devices, then triggering dynamic URL switching.
 */
class BaseUrlUpdateWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters), KoinComponent {

    private val discoverLocalNetworkDevicesUseCase: DiscoverLocalNetworkDevicesUseCase by inject()
    private val getRemoteAvailableDevicesUseCase: GetRemoteAvailableDevicesUseCase by inject()
    private val saveCurrentDeviceUseCase: SaveCurrentDeviceUseCase by inject()
    private val dynamicUrlSwitchingController: DynamicUrlSwitchingController by inject()

    override suspend fun doWork(): Result {
        return try {
            Timber.d("Starting base URL update worker")

            // 1. Get device from mDNS discovery (one-shot)
            val localDevice = discoverLocalNetworkDevicesUseCase.oneShot(
                DiscoverLocalNetworkDevicesUseCase.Params(
                    serviceType = MDNS_SERVICE_TYPE,
                    serviceName = MDNS_SERVICE_NAME,
                    duration = MDNS_DISCOVERY_TIMEOUT
                )
            )
            Timber.d("Local mDNS device discovered: $localDevice")

            // 2. Get devices from remote access API
            val remoteCurrentDevice = getRemoteAvailableDevicesUseCase.currentDevice()
            Timber.d("Remote devices received: $remoteCurrentDevice")

            // 3. Combine devices - merge local device with matching remote device by certificate
            val combinedDevice = combineDevices(localDevice, remoteCurrentDevice)

            // 4. Update current device in repository if we have combined result
            if (combinedDevice != null) {
                Timber.d("Saving combined device: $combinedDevice")
                saveCurrentDeviceUseCase(combinedDevice)

                // 5. Trigger one-shot dynamic URL switching to select best base URL
                dynamicUrlSwitchingController.oneShotDynamicUrlSwitching()
                Timber.d("Base URL update completed successfully")
            } else {
                Timber.d("No device to update, skipping URL switching")
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Base URL update worker failed")
            Result.failure()
        }
    }

    /**
     * Combines local mDNS device with remote devices.
     * If a matching device (by certificate) is found, merges the available paths.
     */
    private fun combineDevices(localDevice: Device?, remoteCurrentDevice: Device?): Device? {
        if (localDevice == null && remoteCurrentDevice == null) {
            return null
        }

        if (localDevice == null) {
            return remoteCurrentDevice
        }

        if (remoteCurrentDevice == null) {
            return localDevice
        }

        val localCertificate = localDevice.certificateCommonName

        return if (localCertificate == remoteCurrentDevice.certificateCommonName) {
            // Merge local path into remote device's available paths
            val mergedPaths = remoteCurrentDevice.availablePaths.toMutableMap()
            localDevice.availablePaths[DevicePathType.LOCAL]?.let { localPath ->
                mergedPaths[DevicePathType.LOCAL] = localPath
            }

            Device(
                id = remoteCurrentDevice.id,
                name = remoteCurrentDevice.name,
                availablePaths = mergedPaths,
                certificateCommonName = remoteCurrentDevice.certificateCommonName
            )
        } else {
            remoteCurrentDevice
        }
    }

    companion object {
        const val BASE_URL_UPDATE_WORKER = "BASE_URL_UPDATE_WORKER"

        private const val MDNS_SERVICE_TYPE = "_https._tcp"
        private const val MDNS_SERVICE_NAME = "HomeCloud"
        private val MDNS_DISCOVERY_TIMEOUT = 10.seconds
    }
}

