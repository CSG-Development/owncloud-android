package com.owncloud.android.data.device

import com.owncloud.android.data.connectivity.NetworkStateObserver
import com.owncloud.android.domain.device.model.DevicePath
import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.domain.server.usecases.DeviceUrlResolver
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
 * by verifying device availability through DeviceUrlResolver.
 *
 * @property networkStateObserver Observes network connectivity changes
 * @property currentDeviceStorage Storage for device base URLs
 * @property deviceUrlResolver Resolver for finding available device URLs
 */
class BaseUrlChooser(
    private val networkStateObserver: NetworkStateObserver,
    private val currentDeviceStorage: CurrentDeviceStorage,
    private val deviceUrlResolver: DeviceUrlResolver,
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
                Timber.d("BaseUrlChooser: Network state changed: $connectivity, resolving available base URL")

                if (!connectivity.hasAnyNetwork()) {
                    Timber.d("BaseUrlChooser: No network available, returning null")
                    return@map null
                }

                val devicePaths = buildDevicePathsList()
                deviceUrlResolver.resolveAvailableBaseUrl(devicePaths)
            }
            .distinctUntilChanged()
    }

    /**
     * Builds a list of device paths ordered by priority (LOCAL > PUBLIC > REMOTE).
     * Only includes paths that are stored in CurrentDeviceStorage.
     *
     * @return List of device path type and URL pairs
     */
    private fun buildDevicePathsList(): Map<DevicePathType, String> {
        val priorityOrder = listOf(
            DevicePathType.LOCAL,
            DevicePathType.PUBLIC,
            DevicePathType.REMOTE
        )

        return priorityOrder.mapNotNull { pathType ->
            currentDeviceStorage.getDeviceBaseUrl(pathType.name)?.let { url ->
                DevicePath(url, pathType)
            }
        }.associate { it.devicePathType to it.hostUrl }
    }
}

