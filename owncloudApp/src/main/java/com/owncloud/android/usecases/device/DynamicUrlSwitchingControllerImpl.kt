package com.owncloud.android.usecases.device

import android.content.Context
import com.owncloud.android.data.device.DynamicBaseUrlSwitcher
import com.owncloud.android.data.lifecycle.AppLifecycleObserver
import com.owncloud.android.data.remoteaccess.RemoteAccessAuthEvents
import com.owncloud.android.domain.device.usecases.DynamicUrlSwitchingController
import com.owncloud.android.domain.device.usecases.UpdateBaseUrlUseCase
import com.owncloud.android.presentation.authentication.AccountUtils.getCurrentOwnCloudAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Controller wiring [DynamicBaseUrlSwitcher] into the application lifecycle.
 *
 * Responsibilities:
 *  - Start the switcher on init for the current account; the switcher itself owns the
 *    lifecycle gating (skip-first / debounce / cooldown / background-defer) so the
 *    controller does NOT stop it on BACKGROUND anymore.
 *  - Bridge [RemoteAccessAuthEvents.sessionInvalid] events to
 *    [UpdateBaseUrlUseCase.notifyTokenRequired] so a single emission is preserved by the
 *    use case's conflated flow and reaches the UI subscriber.
 */
class DynamicUrlSwitchingControllerImpl(
    private val appContext: Context,
    private val dynamicBaseUrlSwitcher: DynamicBaseUrlSwitcher,
    private val coroutineScope: CoroutineScope,
    private val authEvents: RemoteAccessAuthEvents,
    private val updateBaseUrlUseCase: UpdateBaseUrlUseCase,
) : DynamicUrlSwitchingController {

    private var authBridgeJob: Job? = null

    override fun initDynamicUrlSwitching() {
        startAuthBridge()
        startDynamicUrlSwitching(fromBackground = true)
    }

    override fun startDynamicUrlSwitching(fromBackground: Boolean) {
        val account = getCurrentOwnCloudAccount(appContext)
        if (account == null) {
            Timber.d("DynamicUrlSwitchingController: no current account, skipping start")
            return
        }
        dynamicBaseUrlSwitcher.startDynamicUrlSwitching(account, fromBackground)
    }

    override fun stopDynamicUrlSwitching() {
        dynamicBaseUrlSwitcher.stopDynamicUrlSwitching()
    }

    private fun startAuthBridge() {
        if (authBridgeJob?.isActive == true) return
        authBridgeJob = coroutineScope.launch {
            authEvents.sessionInvalid.collect {
                Timber.d("DynamicUrlSwitchingController: forwarding sessionInvalid -> tokenRequired")
                updateBaseUrlUseCase.notifyTokenRequired()
            }
        }
    }
}
