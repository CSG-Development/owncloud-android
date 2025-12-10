package com.owncloud.android.domain.device

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.owncloud.android.domain.device.usecases.GetBestAvailableBaseUrlUseCase
import com.owncloud.android.domain.mdnsdiscovery.usecases.DiscoverLocalNetworkDevicesUseCase
import com.owncloud.android.domain.remoteaccess.usecases.GetRemoteAvailableDevicesUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.IOException

/**
 * Worker responsible for updating base URLs by combining mDNS discovery
 * and remote access devices, then selecting the best available URL.
 *
 * The worker performs the following steps:
 * 1. Try to choose best available base URL from currently saved paths
 * 2. If step 1 fails (no URL available), sync new base URLs from mDNS and remote API
 * 3. Try to choose best available base URL again with updated paths
 */
class BaseUrlUpdateWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters), KoinComponent {

    private val discoverLocalNetworkDevicesUseCase: DiscoverLocalNetworkDevicesUseCase by inject()
    private val getRemoteAvailableDevicesUseCase: GetRemoteAvailableDevicesUseCase by inject()
    private val saveCurrentDeviceUseCase: SaveCurrentDeviceUseCase by inject()
    private val accountBaseUrlManager: AccountBaseUrlManager by inject()

    private val getBestAvailableBaseUrlUseCase: GetBestAvailableBaseUrlUseCase by inject()

    override suspend fun doWork(): Result {
        return try {
            Timber.d("BaseUrlUpdateWorker: Starting base URL update")

            if (!accountBaseUrlManager.hasActiveAccount()) {
                Timber.d("BaseUrlUpdateWorker: No active account, skipping")
                return Result.success()
            }

            // Step 1: Try to choose best available base URL from current paths
            val updatedFromCurrentPaths = getBestAvailableBaseUrlUseCase.updateBestAvailableBaseUrl()

            if (updatedFromCurrentPaths) {
                // Successfully updated from current paths, no need to sync
                Timber.d("BaseUrlUpdateWorker: Base URL updated from current paths, done")
                return Result.success()
            }

            // Step 2: Current paths didn't work, sync new base URLs from mDNS and remote API
            Timber.d("BaseUrlUpdateWorker: No valid URL from current paths, syncing new paths")
            if (syncDevicePaths()) {
                // Step 3: Try to choose best available base URL again with updated paths
                getBestAvailableBaseUrlUseCase.updateBestAvailableBaseUrl()
            }

            Timber.d("BaseUrlUpdateWorker: Base URL update completed successfully")
            Result.success()
        } catch (e: IOException) {
            Timber.e(e, "BaseUrlUpdateWorker: Failed with IO error - ${e.message}")
            Result.retry()
        } catch (e: Exception) {
            Timber.e(e, "BaseUrlUpdateWorker: Failed - ${e.message}")
            Result.failure()
        }
    }

    /**
     * Syncs device paths from mDNS discovery and remote API.
     */
    private suspend fun syncDevicePaths(): Boolean {
        if (!getRemoteAvailableDevicesUseCase.hasToken()) {
            return false
        }
        Timber.d("BaseUrlUpdateWorker: Syncing device paths from mDNS and remote API")

        val localDevice = discoverLocalNetworkDevicesUseCase.oneShot(
            DiscoverLocalNetworkDevicesUseCase.DEFAULT_MDNS_PARAMS
        )
        Timber.d("BaseUrlUpdateWorker: Local mDNS device discovered: $localDevice")

        if (localDevice != null) {
            saveCurrentDeviceUseCase(localDevice)
        } else {
            val remoteCurrentDevice = getRemoteAvailableDevicesUseCase.currentDevice()
            Timber.d("BaseUrlUpdateWorker: Remote current device: $remoteCurrentDevice")
            if (remoteCurrentDevice != null) {
                saveCurrentDeviceUseCase(remoteCurrentDevice)
            } else {
                Timber.d("BaseUrlUpdateWorker: No device found from mDNS or remote API")
                return false
            }
        }
        return true
    }

    companion object {
        const val BASE_URL_UPDATE_WORKER = "BASE_URL_UPDATE_WORKER"
    }
}