package com.owncloud.android.domain.device

import kotlinx.coroutines.flow.Flow

interface BaseUrlUpdateStatus {

    fun isInProgress(): Boolean

    fun observe(): Flow<Boolean>
}
