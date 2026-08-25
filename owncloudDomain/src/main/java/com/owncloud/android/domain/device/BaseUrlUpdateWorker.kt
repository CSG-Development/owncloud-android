package com.owncloud.android.domain.device

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.owncloud.android.domain.device.usecases.SwitchToBestAvailableBaseUrlUseCase
import com.owncloud.android.domain.device.usecases.SyncCurrentDevicePathsUseCase
import com.owncloud.android.domain.device.usecases.UpdateBaseUrlUseCase
import com.owncloud.android.domain.remoteaccess.usecases.GetRemoteAccessTokenUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.IOException

/**
 * Worker responsible for updating the base URL.
 *
 * Aligned with reference Algorithms A and B:
 *  1. Try [SwitchToBestAvailableBaseUrlUseCase] first. The chooser is responsible for
 *     reading the cached paths, refreshing them via the Remote Access backend when the
 *     cache is expired (cheap fast-path: when [seagateDeviceId] is known we never need to
 *     re-run mDNS for paths only) and falling back to the relay path.
 *  2. If no cached paths exist or the chooser cannot find a reachable URL, run a full
 *     discovery cycle: mDNS + Remote Access enumeration, MERGE the results by
 *     `certificateCommonName` (Phase 1 + Phase 2), persist the merged device (including
 *     the seagateDeviceID and a fresh cache timestamp) and try the chooser one more time.
 */
class BaseUrlUpdateWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters), KoinComponent {

    private val getRemoteAccessTokenUseCase: GetRemoteAccessTokenUseCase by inject()
    private val accountBaseUrlManager: AccountBaseUrlManager by inject()

    private val switchToBestAvailableBaseUrlUseCase: SwitchToBestAvailableBaseUrlUseCase by inject()
    private val syncCurrentDevicePathsUseCase: SyncCurrentDevicePathsUseCase by inject()
    private val updateBaseUrlUseCase: UpdateBaseUrlUseCase by inject()

    override suspend fun doWork(): Result {
        return try {
            val fromBackground = inputData.getBoolean(KEY_FROM_BACKGROUND, false)
            val wifiAvailable = inputData.getBoolean(KEY_WIFI_AVAILABLE, true)
            setProgress(workDataOf(KEY_FROM_BACKGROUND to fromBackground))

            Timber.d(
                "BaseUrlUpdateWorker: starting (fromBackground=$fromBackground, wifiAvailable=$wifiAvailable)"
            )
            if (!accountBaseUrlManager.hasActiveAccount()) {
                Timber.d("BaseUrlUpdateWorker: no active account, skipping")
                return Result.success()
            }

            // Step 1: try chooser with whatever is cached (also handles fast-path refresh).
            val updatedFromCurrentPaths = switchToBestAvailableBaseUrlUseCase.execute(wifiAvailable)
            if (updatedFromCurrentPaths) {
                Timber.d("BaseUrlUpdateWorker: base URL updated from cached/refreshed paths")
                return Result.success()
            }

            // Step 2 (full discovery) requires a Remote Access token. If we have no token,
            // do NOT silently fail — surface the token-required signal so the UI can
            // prompt for re-authentication. This signal is only emitted here (after the
            // cached priority paths have proven unreachable) so a user that is on a
            // working local-only path is never prompted on routine network changes.
            if (!getRemoteAccessTokenUseCase.hasToken()) {
                Timber.d("BaseUrlUpdateWorker: cached paths unreachable AND no Remote Access token, requesting token")
                updateBaseUrlUseCase.notifyTokenRequired()
                return Result.success()
            }

            // Step 2: full discovery cycle (mDNS Phase 1 + Remote Phase 2 merged).
            Timber.d("BaseUrlUpdateWorker: cached paths failed, running full discovery")
            if (syncCurrentDevicePathsUseCase.execute(wifiAvailable)) {
                switchToBestAvailableBaseUrlUseCase.execute(wifiAvailable)
            }
            Timber.d("BaseUrlUpdateWorker: completed")
            Result.success()
        } catch (e: IOException) {
            Timber.e(e, "BaseUrlUpdateWorker: failed with IO error - ${e.message}")
            Result.retry()
        } catch (e: Exception) {
            Timber.e(e, "BaseUrlUpdateWorker: failed - ${e.message}")
            Result.failure()
        }
    }

    companion object {
        const val BASE_URL_UPDATE_WORKER = "BASE_URL_UPDATE_WORKER"
        const val KEY_FROM_BACKGROUND = "KEY_FROM_BACKGROUND"
        const val KEY_WIFI_AVAILABLE = "KEY_WIFI_AVAILABLE"
    }
}
