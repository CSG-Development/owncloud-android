package com.owncloud.android.domain.device

import kotlinx.coroutines.flow.Flow

interface AppLifecycleGateway {

    fun isForeground(): Boolean

    fun observe(): Flow<AppForegroundState>
}
