package com.owncloud.android.account

import android.accounts.Account
import android.accounts.AccountManager
import com.owncloud.android.data.authentication.KEY_RA_ACCESS_TOKEN
import com.owncloud.android.data.authentication.KEY_RA_FAVORITE_DEVICE_CERT_COMMON_NAME
import com.owncloud.android.data.authentication.KEY_RA_REFRESH_TOKEN
import com.owncloud.android.data.device.CurrentDeviceStorage
import com.owncloud.android.data.remoteaccess.RemoteAccessTokenStorage
import com.owncloud.android.lib.common.accounts.AccountDataStorage
import com.owncloud.android.providers.AccountProvider

class HCAccountDataStorage(
    private val accountManager: AccountManager,
    private val accountProvider: AccountProvider,
    private val tokenStorage: RemoteAccessTokenStorage,
    private val currentDeviceStorage: CurrentDeviceStorage,
) : AccountDataStorage {

    override fun saveDeviceCertCommonName(deviceCertCommonName: String) {
        val currentAccount = accountProvider.getCurrentOwnCloudAccount()
        currentAccount?.let { account ->
            currentDeviceStorage.getCertificateCommonName()?.let {
                accountManager.setUserData(account, KEY_RA_FAVORITE_DEVICE_CERT_COMMON_NAME, it)
            }
        }
    }

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

    override fun clearAll() {
        val currentAccount = accountProvider.getCurrentOwnCloudAccount()
        currentAccount?.let { account ->
            accountManager.setUserData(account, KEY_RA_ACCESS_TOKEN, null)
            accountManager.setUserData(account, KEY_RA_REFRESH_TOKEN, null)
            accountManager.setUserData(account, KEY_RA_FAVORITE_DEVICE_CERT_COMMON_NAME, null)
        }
    }
}