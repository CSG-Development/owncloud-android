package com.owncloud.android.data.device

import com.owncloud.android.domain.device.NetworkRequestDispatcher
import com.owncloud.android.lib.common.SingleSessionManager

class HCNetworkRequestDispatcher : NetworkRequestDispatcher {

    override fun cancelAllRequests() {
        SingleSessionManager.getDefaultSingleton().cancelAllRequests()
    }
}