package com.owncloud.android.usecases.device

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.owncloud.android.workers.BaseUrlUpdateWorker
import timber.log.Timber
import java.util.UUID

/**
 * Use case to trigger base URL update by combining mDNS discovery
 * and remote access devices, then performing dynamic URL switching.
 *
 * Uses [WorkManager] to run [BaseUrlUpdateWorker] which:
 * 1. Discovers local device via mDNS
 * 2. Fetches remote devices from API
 * 3. Combines and saves the device paths
 * 4. Triggers one-shot dynamic URL switching
 */
class UpdateBaseUrlUseCase(
    private val workManager: WorkManager
) {

    fun execute(): UUID {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val baseUrlUpdateWork = OneTimeWorkRequestBuilder<BaseUrlUpdateWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            BaseUrlUpdateWorker.BASE_URL_UPDATE_WORKER,
            ExistingWorkPolicy.REPLACE,
            baseUrlUpdateWork
        )

        Timber.i("Base URL update worker has been enqueued.")

        return baseUrlUpdateWork.id
    }
}

