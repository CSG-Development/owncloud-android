package com.owncloud.android.data.device.usecases

import android.accounts.Account
import com.owncloud.android.data.device.DynamicBaseUrlSwitcher
import com.owncloud.android.domain.device.usecases.ManageDynamicUrlSwitchingUseCase

/**
 * Implementation of ManageDynamicUrlSwitchingUseCase.
 * 
 * Delegates all operations to DynamicBaseUrlSwitcher.
 */
class ManageDynamicUrlSwitchingUseCaseImpl(
    private val dynamicBaseUrlSwitcher: DynamicBaseUrlSwitcher
) : ManageDynamicUrlSwitchingUseCase {

    override fun startDynamicUrlSwitching(account: Account) {
        dynamicBaseUrlSwitcher.startDynamicUrlSwitching(account)
    }

    override fun stopDynamicUrlSwitching() {
        dynamicBaseUrlSwitcher.stopDynamicUrlSwitching()
    }

    override fun isActive(): Boolean {
        return dynamicBaseUrlSwitcher.isActive()
    }

    override fun getCurrentAccount(): Account? {
        return dynamicBaseUrlSwitcher.getCurrentAccount()
    }
}

