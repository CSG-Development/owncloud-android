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

class NetworkMonitorViewModel(
    private val deviceUrlResolver: DeviceUrlResolver,
    private val getCurrentDevicePathsUseCase: GetCurrentDevicePathsUseCase,
    private val networkStateObserver: NetworkStateObserver,
    private val appLifecycleObserver: AppLifecycleObserver,
    private val dispatchers: CoroutinesDispatcherProvider,
) : ViewModel() {

    private val _isNetworkUnavailable = MutableSharedFlow<Boolean>(replay = 1)
    val isNetworkUnavailable: SharedFlow<Boolean> = _isNetworkUnavailable.asSharedFlow()

    private var probeJob: Job? = null

    init {
        viewModelScope.launch {
            var wasNoNetwork = false
            combine(
                networkStateObserver.observeNetworkState(),
                appLifecycleObserver.appState
            ) { connectivity, appState ->
                Pair(connectivity.hasAnyNetwork(), appState == AppState.FOREGROUND)
            }.collect { (hasNetwork, isForeground) ->
                when {
                    isForeground && hasNetwork -> {
                        val probeImmediately = wasNoNetwork
                        wasNoNetwork = false
                        if (probeImmediately) _isNetworkUnavailable.emit(false)
                        restartProbeLoop(immediate = probeImmediately)
                    }
                    isForeground && !hasNetwork -> {
                        wasNoNetwork = true
                        stopProbeLoop()
                        _isNetworkUnavailable.emit(true)
                    }
                    else -> {
                        wasNoNetwork = false
                        stopProbeLoop()
                        _isNetworkUnavailable.emit(false)
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
            _isNetworkUnavailable.emit(false)
            return
        }
        try {
            val result = deviceUrlResolver.resolveAvailableBaseUrl(paths)
            Timber.d("NetworkMonitor: probe result=$result")
            _isNetworkUnavailable.emit(result == null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "NetworkMonitor: probe failed with exception")
            _isNetworkUnavailable.emit(true)
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
