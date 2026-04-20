package com.owncloud.android.domain.device.usecases

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.owncloud.android.domain.device.BaseUrlUpdateWorker
import com.owncloud.android.domain.remoteaccess.usecases.GetRemoteAccessTokenUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.util.UUID

/**
 * Use case to trigger base URL update by combining mDNS discovery
 * and remote access devices, then performing dynamic URL switching.
 *
 * Uses [androidx.work.WorkManager] to run [com.owncloud.android.domain.device.BaseUrlUpdateWorker] which:
 * 1. Discovers local device via mDNS
 * 2. Fetches remote devices from API
 * 3. Combines and saves the device paths
 * 4. Triggers one-shot dynamic URL switching
 */
class UpdateBaseUrlUseCase(
    private val workManager: WorkManager,
    private val getRemoteAccessTokenUseCase: GetRemoteAccessTokenUseCase,
) {

    // Conflated buffer so a single tokenRequired signal is not lost if no subscriber is
    // present at emission time. Only the most recent signal is retained.
    private val _tokenRequired: MutableSharedFlow<Unit> = MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val tokenRequired: Flow<Unit> = _tokenRequired.asSharedFlow()

    suspend fun execute(
        fromBackground: Boolean = false,
        wifiAvailable: Boolean = true,
    ): UUID? {
        if (!getRemoteAccessTokenUseCase.hasToken()) {
            _tokenRequired.emit(Unit)
            return null
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(
            BaseUrlUpdateWorker.KEY_FROM_BACKGROUND to fromBackground,
            BaseUrlUpdateWorker.KEY_WIFI_AVAILABLE to wifiAvailable,
        )

        val baseUrlUpdateWork = OneTimeWorkRequestBuilder<BaseUrlUpdateWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork(
            BaseUrlUpdateWorker.BASE_URL_UPDATE_WORKER,
            ExistingWorkPolicy.REPLACE,
            baseUrlUpdateWork
        )

        Timber.i(
            "Base URL update worker has been enqueued (fromBackground=$fromBackground, wifiAvailable=$wifiAvailable)."
        )

        return baseUrlUpdateWork.id
    }

    /**
     * Notifies subscribers that Remote Access re-authentication is required. Used by
     * downstream auth components (e.g. token-refresh interceptor) when the session can no
     * longer recover.
     */
    suspend fun notifyTokenRequired() {
        _tokenRequired.emit(Unit)
    }

    fun hasScheduled(): Boolean {
        val state = workManager.getWorkInfosForUniqueWork(BaseUrlUpdateWorker.BASE_URL_UPDATE_WORKER).get().firstOrNull()?.state
        return state == androidx.work.WorkInfo.State.ENQUEUED || state == androidx.work.WorkInfo.State.RUNNING
    }
}
