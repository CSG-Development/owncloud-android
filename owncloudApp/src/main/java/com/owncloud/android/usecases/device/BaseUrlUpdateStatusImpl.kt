package com.owncloud.android.usecases.device

import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.owncloud.android.domain.device.BaseUrlUpdateStatus
import com.owncloud.android.domain.device.BaseUrlUpdateWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

class BaseUrlUpdateStatusImpl(
    private val workManager: WorkManager,
) : BaseUrlUpdateStatus {

    override fun isInProgress(): Boolean {
        val workers = workManager
            .getWorkInfosForUniqueWork(BaseUrlUpdateWorker.BASE_URL_UPDATE_WORKER)
            .get()
        val firstWorker = workers.firstOrNull()
        val state = firstWorker?.state
        val id = firstWorker?.id
        Timber.i("BaseUrlUpdateWorker state: $state, id: $id, total workers: ${workers?.size}")
        return firstWorker.isInProgress()
    }

    override fun observe(): Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkLiveData(BaseUrlUpdateWorker.BASE_URL_UPDATE_WORKER)
            .asFlow()
            .mapNotNull { it?.firstOrNull()?.isInProgress() ?: false }
            .distinctUntilChanged()
            .onEach {
                Timber.i("BaseUrlUpdateWorker is in progress: $it")
            }

    private fun WorkInfo?.isInProgress(): Boolean {
        return this != null && (state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING)
    }
}
