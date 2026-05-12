package com.owncloud.android.presentation.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.data.connectivity.NetworkStateObserver
import com.owncloud.android.data.lifecycle.AppLifecycleObserver
import com.owncloud.android.data.lifecycle.AppState
import com.owncloud.android.domain.device.usecases.GetCurrentDevicePathsUseCase
import com.owncloud.android.domain.server.usecases.DeviceUrlResolver
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

sealed class NetworkMonitorState {
    object Hidden : NetworkMonitorState()
    object NoInternet : NetworkMonitorState()
    object FindingNetwork : NetworkMonitorState()
}

class NetworkMonitorViewModel(
    private val deviceUrlResolver: DeviceUrlResolver,
    private val getCurrentDevicePathsUseCase: GetCurrentDevicePathsUseCase,
    private val networkStateObserver: NetworkStateObserver,
    private val appLifecycleObserver: AppLifecycleObserver,
    private val dispatchers: CoroutinesDispatcherProvider,
) : ViewModel() {

    private val _networkMonitorState = MutableSharedFlow<NetworkMonitorState>(replay = 1)
    val networkMonitorState: SharedFlow<NetworkMonitorState> = _networkMonitorState.asSharedFlow()

    private var probeJob: Job? = null

    init {
        viewModelScope.launch {
            var lastAppState: AppState? = null
            combine(
                networkStateObserver.observeNetworkState(),
                appLifecycleObserver.appState
            ) { connectivity, appState ->
                Pair(connectivity.hasAnyNetwork(), appState)
            }.collect { (hasNetwork, appState) ->
                val isForeground = appState == AppState.FOREGROUND
                val justCameToForeground = isForeground && lastAppState != AppState.FOREGROUND
                lastAppState = appState

                when {
                    isForeground && hasNetwork -> {
                        if (justCameToForeground) {
                            // fresh foreground entry — wait 30s before first probe
                            restartProbeLoop(immediate = false)
                        } else {
                            // network changed while already in foreground — hide snackbar and probe now
                            _networkMonitorState.emit(NetworkMonitorState.Hidden)
                            restartProbeLoop(immediate = true)
                        }
                    }
                    isForeground && !hasNetwork -> {
                        stopProbeLoop()
                        _networkMonitorState.emit(NetworkMonitorState.NoInternet)
                    }
                    else -> { // backgrounded
                        stopProbeLoop()
                        _networkMonitorState.emit(NetworkMonitorState.Hidden)
                    }
                }
            }
        }
    }

    private fun restartProbeLoop(immediate: Boolean = false) {
        stopProbeLoop()
        probeJob = viewModelScope.launch(dispatchers.io) {
            if (!immediate) delay(PROBE_INTERVAL_MS)
            while (true) {
                runProbe()
                delay(PROBE_INTERVAL_MS)
            }
        }
    }

    private suspend fun runProbe() {
        val paths = getCurrentDevicePathsUseCase()
        if (paths.isEmpty()) {
            _networkMonitorState.emit(NetworkMonitorState.Hidden)
            return
        }
        try {
            val result = deviceUrlResolver.resolveAvailableBaseUrl(paths)
            Timber.d("NetworkMonitor: probe result=$result")
            _networkMonitorState.emit(
                if (result == null) NetworkMonitorState.FindingNetwork else NetworkMonitorState.Hidden
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "NetworkMonitor: probe failed with exception")
            _networkMonitorState.emit(NetworkMonitorState.FindingNetwork)
        }
    }

    private fun stopProbeLoop() {
        probeJob?.cancel()
        probeJob = null
    }

    companion object {
        const val PROBE_INTERVAL_MS = 30_000L
    }
}
