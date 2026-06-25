package com.owncloud.android.data.device

import com.owncloud.android.data.lifecycle.AppLifecycleObserver
import com.owncloud.android.data.lifecycle.AppState
import com.owncloud.android.domain.device.AppForegroundState
import com.owncloud.android.domain.device.AppLifecycleGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppLifecycleGatewayImpl(
    private val appLifecycleObserver: AppLifecycleObserver,
) : AppLifecycleGateway {

    override fun isForeground(): Boolean = appLifecycleObserver.isInForeground()

    override fun observe(): Flow<AppForegroundState> = appLifecycleObserver.appState.map { state ->
        when (state) {
            AppState.FOREGROUND -> AppForegroundState.FOREGROUND
            AppState.BACKGROUND -> AppForegroundState.BACKGROUND
        }
    }
}
