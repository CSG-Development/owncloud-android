package com.owncloud.android.usecases.device

import android.content.Context
import com.owncloud.android.data.device.DynamicBaseUrlSwitcher
import com.owncloud.android.domain.device.UrlSwitchingTrigger
import com.owncloud.android.presentation.authentication.AccountUtils.getCurrentOwnCloudAccount
import timber.log.Timber

class UrlSwitchingTriggerImpl(
    private val appContext: Context,
    private val dynamicBaseUrlSwitcher: DynamicBaseUrlSwitcher,
) : UrlSwitchingTrigger {

    override fun startUrlSwitching(fromBackground: Boolean) {
        val account = getCurrentOwnCloudAccount(appContext)
        if (account == null) {
            Timber.d("UrlSwitchingTrigger: no current account, skipping start")
            return
        }
        dynamicBaseUrlSwitcher.startDynamicUrlSwitching(account, fromBackground)
    }

    override fun stopUrlSwitching() {
        dynamicBaseUrlSwitcher.stopDynamicUrlSwitching()
    }
}
