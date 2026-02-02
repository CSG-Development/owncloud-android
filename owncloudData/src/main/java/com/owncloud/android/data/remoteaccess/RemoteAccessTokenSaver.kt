package com.owncloud.android.data.remoteaccess

import android.accounts.Account

interface RemoteAccessTokenSaver {

    fun saveTokensToAccount(account: Account)

    fun saveTokensToCurrentAccount()

    fun clearTokensFromCurrentAccount()

}