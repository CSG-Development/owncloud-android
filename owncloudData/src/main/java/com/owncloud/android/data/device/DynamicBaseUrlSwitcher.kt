package com.owncloud.android.data.device

import android.accounts.Account
import android.os.SystemClock
import com.owncloud.android.data.connectivity.Connectivity
import com.owncloud.android.data.connectivity.NetworkStateObserver
import com.owncloud.android.data.lifecycle.AppLifecycleObserver
import com.owncloud.android.data.lifecycle.AppState
import com.owncloud.android.domain.device.usecases.UpdateBaseUrlUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Manages dynamic base URL switching for an account based on network conditions.
 *
 * Behavior (aligned with the device-detection reference algorithm D):
 * - Triggers an initial detection immediately when [startDynamicUrlSwitching] is called
 *   (this represents the "foreground start / login completed" entry point).
 * - Observes network state changes via [NetworkStateObserver]:
 *     * the very first emission is treated as the initial known state and ignored;
 *     * subsequent changes are debounced for [DEBOUNCE_MS] ms;
 *     * a minimum cooldown of [COOLDOWN_MS] ms between detections is enforced;
 *     * if the app is in background the change is marked as pending and processed when
 *       the app returns to foreground;
 *     * the WiFi/LAN-likely state is forwarded to the use case so local probing can be
 *       skipped when only cellular is available.
 * - Observes [AppLifecycleObserver] to re-process pending changes on FOREGROUND.
 */
@OptIn(FlowPreview::class)
class DynamicBaseUrlSwitcher(
    private val networkStateObserver: NetworkStateObserver,
    private val appLifecycleObserver: AppLifecycleObserver,
    private val coroutineScope: CoroutineScope,
    private val updateBaseUrlUseCase: UpdateBaseUrlUseCase,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val cooldownMs: Long = COOLDOWN_MS,
    private val timeProvider: () -> Long = { SystemClock.elapsedRealtime() },
) {

    private var observationJob: Job? = null
    private var lifecycleJob: Job? = null
    private var currentAccount: Account? = null

    // State guarded by [stateMutex]
    private val stateMutex = Mutex()
    private var isInitialForegroundCheck: Boolean = false
    private var lastDetectionAtMs: Long = 0L
    private var pendingChange: Connectivity? = null

    /**
     * Start observing network/lifecycle state and triggering URL updates for the given account.
     *
     * - Cancels any previous observation and resets the gating state.
     * - Triggers an immediate detection (representing the foreground/start entry point).
     * - Starts the network observer with skip-first / debounce / cooldown / background-defer.
     */
    fun startDynamicUrlSwitching(account: Account, fromBackground: Boolean) {
        stopDynamicUrlSwitching()
        currentAccount = account
        isInitialForegroundCheck = fromBackground
        lastDetectionAtMs = 0L
        pendingChange = null

        Timber.d(
            "DynamicBaseUrlSwitcher: starting dynamic URL switching for account=${account.name}, fromBackground=$fromBackground"
        )

        observationJob = coroutineScope.launch {
            // Initial trigger represents the entry-point detection. It is not gated by the
            // skip-first rule but is still subject to the cooldown so that overlapping
            // start calls do not double-trigger.
            triggerInitialDetection()

            networkStateObserver.observeNetworkState()
                .distinctUntilChanged()
                .drop(1) // skip the very first emission, which is the initial known state
                .debounce(debounceMs)
                .catch { error ->
                    Timber.e(error, "DynamicBaseUrlSwitcher: error observing network state")
                }
                .collect { connectivity -> handleConnectivityChange(connectivity) }
        }

        lifecycleJob = coroutineScope.launch {
            appLifecycleObserver.appState
                .drop(1) // ignore initial state, that's owned by startDynamicUrlSwitching
                .collect { state ->
                    if (state == AppState.FOREGROUND) {
                        processPendingIfAny()
                    }
                }
        }
    }

    private suspend fun triggerInitialDetection() {
        val initialConnectivity = Connectivity(setOf(Connectivity.ConnectionType.WIFI))
        // The initial detection always runs (lastDetectionAtMs == 0L bypasses cooldown).
        triggerDetection(initialConnectivity, force = true)
    }

    private suspend fun handleConnectivityChange(connectivity: Connectivity) {
        Timber.d("DynamicBaseUrlSwitcher: network state changed: $connectivity")

        if (!connectivity.hasAnyNetwork()) {
            Timber.d("DynamicBaseUrlSwitcher: no network available, skipping update")
            return
        }

        if (appLifecycleObserver.isInBackground()) {
            Timber.d("DynamicBaseUrlSwitcher: app in background, deferring change")
            stateMutex.withLock { pendingChange = connectivity }
            return
        }

        triggerDetection(connectivity, force = false)
    }

    private suspend fun processPendingIfAny() {
        val pending = stateMutex.withLock {
            val p = pendingChange
            pendingChange = null
            p
        } ?: return
        Timber.d("DynamicBaseUrlSwitcher: processing pending change after foreground")
        triggerDetection(pending, force = false)
    }

    private suspend fun triggerDetection(connectivity: Connectivity, force: Boolean) {
        val now = timeProvider()
        val fromBackground: Boolean
        stateMutex.withLock {
            if (!force && lastDetectionAtMs != 0L) {
                val sinceLast = now - lastDetectionAtMs
                if (sinceLast < cooldownMs) {
                    Timber.d(
                        "DynamicBaseUrlSwitcher: cooldown not elapsed (${sinceLast}ms < ${cooldownMs}ms), skipping"
                    )
                    return
                }
            }
            lastDetectionAtMs = now
            fromBackground = isInitialForegroundCheck
            isInitialForegroundCheck = false
        }

        // Gating rule (reference): try local discovery only when we have a LAN-capable
        // transport, OR when connectivity is unknown. Cellular-only ⇒ skip local.
        val wifiAvailable = connectivity.isLanLikely() || connectivity.isWifiStateUnknown()
        Timber.d(
            "DynamicBaseUrlSwitcher: triggering update (fromBackground=$fromBackground, wifiAvailable=$wifiAvailable)"
        )
        updateBaseUrlUseCase.execute(fromBackground = fromBackground, wifiAvailable = wifiAvailable)
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
        lifecycleJob?.cancel()
        lifecycleJob = null

        currentAccount?.let {
            Timber.d("DynamicBaseUrlSwitcher: stopped dynamic URL switching for account: ${it.name}")
        }

        currentAccount = null
        pendingChange = null
        lastDetectionAtMs = 0L
    }

    /**
     * Check if dynamic URL switching is currently active.
     */
    fun isActive(): Boolean = observationJob?.isActive == true

    /**
     * Cancel all ongoing operations and clean up resources.
     * Should be called when the switcher is no longer needed.
     */
    fun dispose() {
        stopDynamicUrlSwitching()
        coroutineScope.cancel()
    }

    companion object {
        const val DEBOUNCE_MS: Long = 3_000L
        const val COOLDOWN_MS: Long = 30_000L
    }
}
