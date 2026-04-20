package com.owncloud.android.domain.device.usecases

import com.owncloud.android.domain.device.AccountBaseUrlManager
import com.owncloud.android.domain.device.BaseUrlChooser
import com.owncloud.android.domain.device.NetworkRequestDispatcher
import timber.log.Timber

class SwitchToBestAvailableBaseUrlUseCase(
    private val baseUrlChooser: BaseUrlChooser,
    private val accountBaseUrlManager: AccountBaseUrlManager,
    private val networkRequestDispatcher: NetworkRequestDispatcher,
) {

    suspend fun execute(wifiAvailable: Boolean = true): Boolean {
        val bestBaseUrl = baseUrlChooser.chooseBestAvailableBaseUrl(wifiAvailable = wifiAvailable)
        Timber.d("BaseUrlUpdateWorker: best available base URL: $bestBaseUrl (wifiAvailable=$wifiAvailable)")

        if (bestBaseUrl == null) {
            Timber.d("BaseUrlUpdateWorker: no valid base URL available")
            return false
        }

        val currentBaseUrl = accountBaseUrlManager.getCurrentBaseUrl()

        return when {
            currentBaseUrl == bestBaseUrl -> {
                Timber.d("BaseUrlUpdateWorker: base URL unchanged: $currentBaseUrl")
                true
            }

            else -> {
                Timber.i("BaseUrlUpdateWorker: updating base URL: $currentBaseUrl -> $bestBaseUrl")
                val updated = accountBaseUrlManager.updateBaseUrl(bestBaseUrl)
                if (updated) {
                    networkRequestDispatcher.cancelAllRequests()
                }
                updated
            }
        }
    }
}
