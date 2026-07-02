package com.owncloud.android.usecases.device

import com.owncloud.android.data.remoteaccess.RemoteAccessAuthEvents
import com.owncloud.android.domain.device.DeviceConnectionMonitor
import com.owncloud.android.domain.device.usecases.DynamicUrlSwitchingController
import com.owncloud.android.domain.device.usecases.UpdateBaseUrlUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

class DynamicUrlSwitchingControllerImpl(
    private val deviceConnectionMonitor: DeviceConnectionMonitor,
    private val coroutineScope: CoroutineScope,
    private val authEvents: RemoteAccessAuthEvents,
    private val updateBaseUrlUseCase: UpdateBaseUrlUseCase,
) : DynamicUrlSwitchingController {

    private var authBridgeJob: Job? = null

    override fun initDynamicUrlSwitching() {
        startAuthBridge()
        deviceConnectionMonitor.start(fromBackground = true)
    }

    override fun startDynamicUrlSwitching(fromBackground: Boolean) {
        deviceConnectionMonitor.start(fromBackground)
    }

    override fun stopDynamicUrlSwitching() {
        deviceConnectionMonitor.stop()
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
