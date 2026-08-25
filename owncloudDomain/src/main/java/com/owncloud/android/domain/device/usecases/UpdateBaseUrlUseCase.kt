package com.owncloud.android.domain.device.usecases

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.owncloud.android.domain.device.BaseUrlUpdateWorker
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
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
 *
 * The worker is always enqueued: Step 1 (probing the cached LOCAL/PUBLIC priority paths)
 * does not require a Remote Access token, so a user that authenticated against a purely
 * local path can keep working without being prompted for a verification code on every
 * network change. The token-required signal is only emitted by the worker when the
 * cached priority paths are unreachable AND no token is available (i.e. the user truly
 * needs to re-authenticate against Remote Access to recover).
 */
class UpdateBaseUrlUseCase(
    private val workManager: WorkManager,
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
    ): UUID {
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
            "Base URL update worker has been enqueued (fromBackground=$fromBackground, wifiAvailable=$wifiAvailable), worker ID: ${baseUrlUpdateWork.id}."
        )

        return baseUrlUpdateWork.id
    }

    suspend fun executeAndAwait(
        fromBackground: Boolean = false,
        wifiAvailable: Boolean = true,
    ): Boolean {
        execute(fromBackground, wifiAvailable)
        val completed = withTimeoutOrNull(AWAIT_TIMEOUT_MS) {
            pollUntilUniqueWorkFinishes()
        }
        if (completed == null) {
            Timber.w("Base URL update worker did not finish within ${AWAIT_TIMEOUT_MS}ms")
        }
        return completed ?: false
    }

    private suspend fun pollUntilUniqueWorkFinishes(): Boolean {
        while (true) {
            val workInfo = workManager
                .getWorkInfosForUniqueWork(BaseUrlUpdateWorker.BASE_URL_UPDATE_WORKER)
                .get()
                .firstOrNull()

            when (workInfo?.state) {
                WorkInfo.State.SUCCEEDED -> return true
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> return false
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> {
                    delay(WORK_INFO_POLL_INTERVAL_MS)
                }
                null -> return false
            }
        }
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
        return state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING
    }

    private companion object {
        const val WORK_INFO_POLL_INTERVAL_MS = 250L
        const val AWAIT_TIMEOUT_MS = 15_000L
    }
}
