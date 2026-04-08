package com.owncloud.android.workers

import android.accounts.AccountManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.owncloud.android.MainApp
import com.owncloud.android.domain.tags.usecases.RefreshTagsForAccountUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.concurrent.TimeUnit

class TagsSyncPeriodicWorker(
    val appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParameters
), KoinComponent {

    private val refreshTagsForAccountUseCase: RefreshTagsForAccountUseCase by inject()

    override suspend fun doWork(): Result =
        try {
            val accounts = AccountManager.get(appContext).getAccountsByType(MainApp.accountType)
            Timber.i("Tags sync: refreshing tags for ${accounts.size} account(s)")

            accounts.forEach { account ->
                refreshTagsForAccountUseCase(RefreshTagsForAccountUseCase.Params(accountName = account.name))
            }

            Result.success()
        } catch (exception: Exception) {
            Timber.e(exception, "Sync of tags failed")
            Result.failure()
        }

    companion object {
        const val TAGS_SYNC_PERIODIC_WORKER = "TAGS_SYNC_PERIODIC_WORKER"
        const val repeatInterval: Long = 15L
        val repeatIntervalTimeUnit: TimeUnit = TimeUnit.MINUTES
    }
}
