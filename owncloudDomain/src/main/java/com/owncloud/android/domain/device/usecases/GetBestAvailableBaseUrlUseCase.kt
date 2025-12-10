package com.owncloud.android.domain.device.usecases

import com.owncloud.android.domain.device.AccountBaseUrlManager
import com.owncloud.android.domain.device.BaseUrlChooser
import timber.log.Timber

class GetBestAvailableBaseUrlUseCase(
    private val baseUrlChooser: BaseUrlChooser,
    private val accountBaseUrlManager: AccountBaseUrlManager,
) {

    suspend fun updateBestAvailableBaseUrl(): Boolean {
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
                accountBaseUrlManager.updateBaseUrl(bestBaseUrl)
            }
        }
    }
}