package com.owncloud.android.data.device

import com.owncloud.android.data.connectivity.NetworkStateObserver
import com.owncloud.android.data.mdnsdiscovery.HCDeviceVerificationClient
import com.owncloud.android.domain.device.model.DevicePathType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber
import kotlin.random.Random

/**
 * Dynamically chooses the best available base URL based on network state.
 * 
 * Priority order: LOCAL > PUBLIC > REMOTE
 * 
 * When network state changes, attempts to find the first available base URL
 * by verifying device availability through HCDeviceVerificationClient.
 *
 * @property networkStateObserver Observes network connectivity changes
 * @property currentDeviceStorage Storage for device base URLs
 * @property deviceVerificationClient Client for verifying device availability
 */
class BaseUrlChooser(
    private val networkStateObserver: NetworkStateObserver,
    private val currentDeviceStorage: CurrentDeviceStorage,
    private val deviceVerificationClient: HCDeviceVerificationClient,
) {

    fun observeRandomBaseUrl(): Flow<String?> {
        return networkStateObserver.observeNetworkState()
            .map { baseUrl ->
                val priorityOrder = listOf(
                    DevicePathType.LOCAL,
                    DevicePathType.PUBLIC,
                    DevicePathType.REMOTE
                )
                val pathType = priorityOrder[Random.nextInt(2)]
                currentDeviceStorage.getDeviceBaseUrl(pathType.name)
            }
    }

    /**
     * Observe the best available base URL based on network state.
     * 
     * Emits a new URL whenever:
     * - Network state changes
     * - A different URL becomes available
     * 
     * @return Flow emitting the currently available base URL, or null if none are available
     */
    fun observeAvailableBaseUrl(): Flow<String?> {
        return networkStateObserver.observeNetworkState()
            .map { connectivity ->
                Timber.d("Network state changed: $connectivity, resolving available base URL")
                
                if (!connectivity.hasAnyNetwork()) {
                    Timber.d("No network available, returning null")
                    return@map null
                }
                
                resolveAvailableBaseUrl()
            }
            .distinctUntilChanged()
    }

    /**
     * Resolves the best available base URL by checking each stored URL in priority order.
     * 
     * Priority: LOCAL > PUBLIC > REMOTE
     * 
     * @return The first available base URL, or null if none are available
     */
    private suspend fun resolveAvailableBaseUrl(): String? {
        val priorityOrder = listOf(
            DevicePathType.LOCAL,
            DevicePathType.PUBLIC,
            DevicePathType.REMOTE
        )

        for (pathType in priorityOrder) {
            val baseUrl = currentDeviceStorage.getDeviceBaseUrl(pathType.name)
            
            if (baseUrl == null) {
                Timber.d("No base URL stored for $pathType")
                continue
            }

            Timber.d("Checking availability of $pathType: $baseUrl")
            
            // Remove /files suffix if present for verification
            val verificationUrl = baseUrl.removeSuffix("/files")
            val isAvailable = deviceVerificationClient.verifyDevice(verificationUrl)
            
            if (isAvailable) {
                Timber.d("Found available base URL: $baseUrl ($pathType)")
                return baseUrl
            } else {
                Timber.d("Base URL $baseUrl ($pathType) is not available")
            }
        }

        Timber.d("No available base URLs found")
        return null
    }
}

