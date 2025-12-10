package com.owncloud.android.data.device

import com.owncloud.android.domain.device.BaseUrlChooser
import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.domain.server.usecases.DeviceUrlResolver
import timber.log.Timber

/**
 * Implementation of [BaseUrlChooser] that chooses the best available base URL
 * based on stored device paths.
 *
 * Priority order: LOCAL > PUBLIC > REMOTE
 *
 * Verifies device availability through DeviceUrlResolver before returning.
 */
class HCBaseUrlChooser(
    private val currentDeviceStorage: CurrentDeviceStorage,
    private val deviceUrlResolver: DeviceUrlResolver,
) : BaseUrlChooser {

    /**
     * Chooses the best available base URL from stored device paths.
     *
     * @return The best available base URL, or null if none are available
     */
    override suspend fun chooseBestAvailableBaseUrl(): String? {
        val devicePaths = buildDevicePathsList()
        Timber.d("HCBaseUrlChooser: Resolving best available URL from paths: $devicePaths")
        return deviceUrlResolver.resolveAvailableBaseUrl(devicePaths)
    }

    /**
     * Builds a list of device paths ordered by priority (LOCAL > PUBLIC > REMOTE).
     * Only includes paths that are stored in CurrentDeviceStorage.
     *
     * @return Map of device path type to URL
     */
    private fun buildDevicePathsList(): Map<DevicePathType, String> {
        val priorityOrder = listOf(
            DevicePathType.LOCAL,
            DevicePathType.PUBLIC,
            DevicePathType.REMOTE
        )

        return priorityOrder.mapNotNull { pathType ->
            currentDeviceStorage.getDeviceBaseUrl(pathType.name)?.let {
                pathType to it
            }
        }.associate { it.first to it.second }
    }
}

