package com.owncloud.android.domain.device

import kotlinx.coroutines.flow.Flow

interface NetworkConnectivityGateway {

    fun hasNetwork(): Boolean

    fun allowsLocalPathProbe(): Boolean

    fun observe(): Flow<Unit>
}
