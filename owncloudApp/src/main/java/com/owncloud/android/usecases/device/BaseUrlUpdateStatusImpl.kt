package com.owncloud.android.usecases.device

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.owncloud.android.domain.device.BaseUrlUpdateStatus
import com.owncloud.android.domain.device.BaseUrlUpdateWorker
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

class BaseUrlUpdateStatusImpl(
    private val workManager: WorkManager,
) : BaseUrlUpdateStatus {

    override fun isInProgress(): Boolean {
        val state = workManager
            .getWorkInfosForUniqueWork(BaseUrlUpdateWorker.BASE_URL_UPDATE_WORKER)
            .get()
            .firstOrNull()
            ?.state
        return state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING
    }

    override fun observe(): Flow<Boolean> = flow {
        while (currentCoroutineContext().isActive) {
            emit(isInProgress())
            delay(WORKER_POLL_INTERVAL)
        }
    }.distinctUntilChanged()

    companion object {
        private val WORKER_POLL_INTERVAL = 1.seconds
    }
}
