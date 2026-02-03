package com.owncloud.android.remoteaccess

import android.accounts.Account
import android.accounts.AccountManager
import com.owncloud.android.data.authentication.KEY_RA_ACCESS_TOKEN
import com.owncloud.android.data.authentication.KEY_RA_REFRESH_TOKEN
import com.owncloud.android.data.remoteaccess.RemoteAccessTokenSaver
import com.owncloud.android.data.remoteaccess.RemoteAccessTokenStorage
import com.owncloud.android.providers.AccountProvider

class HCRemoteAccessTokenSaver(
    private val accountManager: AccountManager,
    private val accountProvider: AccountProvider,
    private val tokenStorage: RemoteAccessTokenStorage,
) : RemoteAccessTokenSaver {

    override fun saveTokensToAccount(account: Account) {
        tokenStorage.getAccessToken()?.let {
            accountManager.setUserData(account, KEY_RA_ACCESS_TOKEN, it)
        }

        tokenStorage.getRefreshToken()?.let {
            accountManager.setUserData(account, KEY_RA_REFRESH_TOKEN, it)
        }
    }

    override fun saveTokensToCurrentAccount() {
        val currentAccount = accountProvider.getCurrentOwnCloudAccount()
        currentAccount?.let {
            saveTokensToAccount(it)
        }
    }

    override fun clearTokensFromCurrentAccount() {
        val currentAccount = accountProvider.getCurrentOwnCloudAccount()
        currentAccount?.let { account ->
            accountManager.setUserData(account, KEY_RA_ACCESS_TOKEN, null)
            accountManager.setUserData(account, KEY_RA_REFRESH_TOKEN, null)
        }
    }
}