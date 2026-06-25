package com.owncloud.android.domain.device

import com.owncloud.android.domain.device.usecases.SwitchToBestAvailableBaseUrlUseCase
import com.owncloud.android.domain.device.usecases.UpdateBaseUrlUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class DeviceConnectionMonitorImpl(
    private val switchToBestAvailableBaseUrlUseCase: SwitchToBestAvailableBaseUrlUseCase,
    private val updateBaseUrlUseCase: UpdateBaseUrlUseCase,
    private val accountBaseUrlManager: AccountBaseUrlManager,
    private val networkConnectivity: NetworkConnectivityGateway,
    private val appLifecycle: AppLifecycleGateway,
    private val baseUrlUpdateStatus: BaseUrlUpdateStatus,
    private val urlSwitchingTrigger: UrlSwitchingTrigger,
    private val coroutineScope: CoroutineScope,
) : DeviceConnectionMonitor {

    private val _state = MutableSharedFlow<DeviceConnectionState>()
    override val state: SharedFlow<DeviceConnectionState> = _state.asSharedFlow()

    private var probeJob: Job? = null
    private var observerJob: Job? = null
    private val evaluateMutex = Mutex()

    override fun start(fromBackground: Boolean) {
        urlSwitchingTrigger.startUrlSwitching(fromBackground)
        if (observerJob?.isActive == true) return
        startObservers()
    }

    override fun stop() {
        urlSwitchingTrigger.stopUrlSwitching()
        observerJob?.cancel()
        observerJob = null
        stopProbeLoop()
        _state.tryEmit(DeviceConnectionState.Connected)
    }

    override fun reportUnreachable() {
        if (!accountBaseUrlManager.hasActiveAccount()) return
        coroutineScope.launch {
            _state.emit(DeviceConnectionState.FindingNetwork(isForced = false))
            evaluateConnection()
        }
    }

    override fun reportNoNetwork() {
        if (!accountBaseUrlManager.hasActiveAccount()) return
        _state.tryEmit(DeviceConnectionState.NoInternet)
    }

    override fun reportConnected() {
        if (!accountBaseUrlManager.hasActiveAccount()) return
        _state.tryEmit(DeviceConnectionState.Connected)
    }

    override suspend fun retryConnection() {
        _state.emit(DeviceConnectionState.FindingNetwork(isForced = true))
        val wifiAvailable = networkConnectivity.allowsLocalPathProbe()
        updateBaseUrlUseCase.execute(wifiAvailable = wifiAvailable)
        evaluateConnection()
    }

    override suspend fun evaluateConnection() {
        evaluateMutex.withLock {
            if (!accountBaseUrlManager.hasActiveAccount()) {
                _state.emit(DeviceConnectionState.Connected)
                return
            }

            if (!networkConnectivity.hasNetwork()) {
                _state.emit(DeviceConnectionState.NoInternet)
                return
            }

            if (baseUrlUpdateStatus.isInProgress()) {
                _state.emit(DeviceConnectionState.FindingNetwork(isForced = false))
                return
            }

            _state.emit(DeviceConnectionState.FindingNetwork(isForced = false))
            val wifiAvailable = networkConnectivity.allowsLocalPathProbe()
            val reachable = switchToBestAvailableBaseUrlUseCase.execute(wifiAvailable)
            Timber.d("DeviceConnectionMonitor: path reachable=$reachable wifiAvailable=$wifiAvailable")
            _state.emit(
                if (reachable) {
                    DeviceConnectionState.Connected
                } else {
                    DeviceConnectionState.ConnectionLost
                }
            )
        }
    }

    private fun startObservers() {
        observerJob = coroutineScope.launch {
            launch {
                baseUrlUpdateStatus.observe().collect { inProgress ->
                    if (inProgress) {
                        _state.emit(DeviceConnectionState.FindingNetwork(isForced = false))
                    } else if (canProbe()) {
                        evaluateConnection()
                    }
                }
            }

            var lastForeground = appLifecycle.isForeground()
            combine(
                networkConnectivity.observe(),
                appLifecycle.observe(),
            ) { _, appState -> appState }
                .collect { appState ->
                    val isForeground = appState == AppForegroundState.FOREGROUND
                    val justCameToForeground = isForeground && !lastForeground
                    lastForeground = isForeground
                    val hasNetwork = networkConnectivity.hasNetwork()
                    Timber.d("DeviceConnectionMonitor: hasNetwork=$hasNetwork appState=$appState")

                    when {
                        isForeground && hasNetwork -> {
                            if (justCameToForeground) {
                                restartProbeLoop(immediate = false)
                            } else {
                                restartProbeLoop(immediate = true)
                            }
                        }

                        isForeground && !hasNetwork -> {
                            stopProbeLoop()
                            _state.emit(
                                if (accountBaseUrlManager.hasActiveAccount()) {
                                    DeviceConnectionState.NoInternet
                                } else {
                                    DeviceConnectionState.Connected
                                }
                            )
                        }

                        else -> {
                            stopProbeLoop()
                            _state.emit(DeviceConnectionState.Connected)
                        }
                    }
                }
        }
    }

    private fun canProbe(): Boolean =
        appLifecycle.isForeground() &&
                networkConnectivity.hasNetwork() &&
                accountBaseUrlManager.hasActiveAccount()

    private fun restartProbeLoop(immediate: Boolean = false) {
        if (!accountBaseUrlManager.hasActiveAccount()) return
        stopProbeLoop()
        probeJob = coroutineScope.launch {
            if (!immediate) delay(PROBE_INTERVAL)
            while (true) {
                evaluateConnection()
                delay(PROBE_INTERVAL)
            }
        }
    }

    private fun stopProbeLoop() {
        probeJob?.cancel()
        probeJob = null
    }

    companion object {
        private val PROBE_INTERVAL = 30.seconds
    }
}
