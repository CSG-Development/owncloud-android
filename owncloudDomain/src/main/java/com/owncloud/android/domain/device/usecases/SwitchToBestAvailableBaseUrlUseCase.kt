package com.owncloud.android.domain.device.usecases

import com.owncloud.android.domain.device.AccountBaseUrlManager
import com.owncloud.android.domain.device.BaseUrlChooser
import com.owncloud.android.domain.device.NetworkRequestDispatcher
import timber.log.Timber

class SwitchToBestAvailableBaseUrlUseCase(
    private val baseUrlChooser: BaseUrlChooser,
    private val accountBaseUrlManager: AccountBaseUrlManager,
    private val networkRequestDispatcher: NetworkRequestDispatcher
) {

    suspend fun execute(): Boolean {
        val bestBaseUrl = baseUrlChooser.chooseBestAvailableBaseUrl()
        Timber.d("BaseUrlUpdateWorker: Best available base URL: $bestBaseUrl")

        if (bestBaseUrl == null) {
            Timber.d("BaseUrlUpdateWorker: No valid base URL available")
            return false
        }

        val currentBaseUrl = accountBaseUrlManager.getCurrentBaseUrl()

        return when {
            currentBaseUrl == bestBaseUrl -> {
                Timber.d("BaseUrlUpdateWorker: Base URL unchanged: $currentBaseUrl")
                true
            }

            else -> {
                Timber.i("BaseUrlUpdateWorker: Updating base URL: $currentBaseUrl -> $bestBaseUrl")
                val updated = accountBaseUrlManager.updateBaseUrl(bestBaseUrl)
                if (updated) {
                    networkRequestDispatcher.cancelAllRequests()
                }
                return updated
            }
        }
    }
}