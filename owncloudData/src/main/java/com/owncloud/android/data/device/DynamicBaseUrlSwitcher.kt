package com.owncloud.android.data.device

import android.accounts.Account
import com.owncloud.android.data.connectivity.NetworkStateObserver
import com.owncloud.android.domain.device.usecases.UpdateBaseUrlUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages dynamic base URL switching for an account based on network conditions.
 *
 * This class:
 * - Observes network state changes via NetworkStateObserver
 * - Triggers UpdateBaseUrlUseCase when network state changes
 * - The Worker handles all the logic of choosing and updating the best URL
 *
 * Usage:
 * ```
 * // On login
 * dynamicBaseUrlSwitcher.startDynamicUrlSwitching(account)
 *
 * // On logout
 * dynamicBaseUrlSwitcher.stopDynamicUrlSwitching()
 * ```
 */
class DynamicBaseUrlSwitcher(
    private val networkStateObserver: NetworkStateObserver,
    private val coroutineScope: CoroutineScope,
    private val updateBaseUrlUseCase: UpdateBaseUrlUseCase,
) {

    private var observationJob: Job? = null
    private var currentAccount: Account? = null

    /**
     * Start observing network state and triggering URL updates for the given account.
     *
     * This will:
     * 1. Cancel any previous observation
     * 2. Start observing network state changes
     * 3. Trigger UpdateBaseUrlUseCase when network becomes available
     *
     * @param account The account to manage
     */
    fun startDynamicUrlSwitching(account: Account) {
        stopDynamicUrlSwitching()

        currentAccount = account

        Timber.d("DynamicBaseUrlSwitcher: Starting dynamic URL switching for account: ${account.name}")

        observationJob = coroutineScope.launch {
            networkStateObserver.observeNetworkState()
                .distinctUntilChanged()
                .catch { error ->
                    Timber.e(error, "DynamicBaseUrlSwitcher: Error observing network state")
                }
                .collect { connectivity ->
                    Timber.d("DynamicBaseUrlSwitcher: Network state changed: $connectivity")

                    if (connectivity.hasAnyNetwork()) {
                        Timber.d("DynamicBaseUrlSwitcher: Network available, triggering base URL update")
                        updateBaseUrlUseCase.execute()
                    } else {
                        Timber.d("DynamicBaseUrlSwitcher: No network available, skipping update")
                    }
                }
        }
    }

    /**
     * Stop observing and cancel dynamic URL switching.
     *
     * This should be called when:
     * - User logs out
     * - Account is removed
     * - App is shutting down
     */
    fun stopDynamicUrlSwitching() {
        observationJob?.cancel()
        observationJob = null

        currentAccount?.let {
            Timber.d("DynamicBaseUrlSwitcher: Stopped dynamic URL switching for account: ${it.name}")
        }

        currentAccount = null
    }

    /**
     * Check if dynamic URL switching is currently active.
     *
     * @return true if observing network changes, false otherwise
     */
    fun isActive(): Boolean {
        return observationJob?.isActive == true
    }

    /**
     * Cancel all ongoing operations and clean up resources.
     * Should be called when the switcher is no longer needed.
     */
    fun dispose() {
        stopDynamicUrlSwitching()
        coroutineScope.cancel()
    }
}

