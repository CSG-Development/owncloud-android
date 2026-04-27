package com.owncloud.android.data.remoteaccess

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide event bus for Remote Access authentication failures.
 *
 * Emitted by the [com.owncloud.android.data.remoteaccess.interceptor.RemoteAccessTokenRefreshInterceptor]
 * after the refresh+replay sequence has been exhausted (refresh token rejected or refresh
 * call failed) — the session can no longer be recovered automatically and the user must
 * reauthenticate.
 *
 * The flow is conflated with replay = 1 so a single emission cannot be lost when the
 * subscriber is not yet active (the next subscriber receives the most recent event and
 * can react immediately).
 */
class RemoteAccessAuthEvents {

    private val _sessionInvalid: MutableSharedFlow<Unit> = MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val sessionInvalid: Flow<Unit> = _sessionInvalid.asSharedFlow()

    fun notifySessionInvalid() {
        _sessionInvalid.tryEmit(Unit)
    }
}
