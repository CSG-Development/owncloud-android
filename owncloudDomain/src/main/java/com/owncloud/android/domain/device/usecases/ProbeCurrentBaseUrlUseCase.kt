package com.owncloud.android.domain.device.usecases

import com.owncloud.android.domain.device.AccountBaseUrlManager
import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.domain.server.usecases.DeviceUrlResolver
import timber.log.Timber

class ProbeCurrentBaseUrlUseCase(
    private val accountBaseUrlManager: AccountBaseUrlManager,
    private val getCurrentDevicePathsUseCase: GetCurrentDevicePathsUseCase,
    private val deviceUrlResolver: DeviceUrlResolver,
) {

    suspend fun execute(wifiAvailable: Boolean): Boolean {
        val currentUrl = accountBaseUrlManager.getCurrentBaseUrl()
        if (currentUrl == null) {
            Timber.d("ProbeCurrentBaseUrl: no current base URL")
            return false
        }

        val paths = getCurrentDevicePathsUseCase()
        val isLocal = paths[DevicePathType.LOCAL] == currentUrl
        if (isLocal && !wifiAvailable) {
            Timber.d("ProbeCurrentBaseUrl: LOCAL path unavailable on cellular ($currentUrl)")
            return false
        }

        val reachable = deviceUrlResolver.testSinglePath(currentUrl, isLocal = isLocal)
        Timber.d("ProbeCurrentBaseUrl: current=$currentUrl isLocal=$isLocal reachable=${reachable != null}")
        return reachable != null
    }
}
