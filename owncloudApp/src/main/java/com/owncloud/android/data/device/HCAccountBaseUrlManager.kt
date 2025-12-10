package com.owncloud.android.data.device

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import com.owncloud.android.domain.device.AccountBaseUrlManager
import com.owncloud.android.lib.common.SingleSessionManager
import com.owncloud.android.lib.common.accounts.AccountUtils
import com.owncloud.android.presentation.authentication.AccountUtils.getCurrentOwnCloudAccount
import timber.log.Timber

/**
 * Implementation of [AccountBaseUrlManager] that manages account base URLs
 * using Android AccountManager.
 */
class HCAccountBaseUrlManager(
    private val appContext: Context,
    private val accountManager: AccountManager,
) : AccountBaseUrlManager {

    override fun getCurrentBaseUrl(): String? {
        val account = getCurrentAccount() ?: return null
        return accountManager.getUserData(account, AccountUtils.Constants.KEY_OC_BASE_URL)
    }

    override fun updateBaseUrl(newBaseUrl: String): Boolean {
        val account = getCurrentAccount() ?: run {
            Timber.w("HCAccountBaseUrlManager: No current account found")
            return false
        }

        return try {
            accountManager.setUserData(
                account,
                AccountUtils.Constants.KEY_OC_BASE_URL,
                newBaseUrl
            )
            Timber.d("HCAccountBaseUrlManager: Successfully updated base URL to: $newBaseUrl")
            true
        } catch (e: Exception) {
            Timber.e(e, "HCAccountBaseUrlManager: Failed to update base URL")
            false
        }
    }

    override fun hasActiveAccount(): Boolean {
        return getCurrentAccount() != null
    }

    private fun getCurrentAccount(): Account? {
        return getCurrentOwnCloudAccount(appContext)
    }
}

