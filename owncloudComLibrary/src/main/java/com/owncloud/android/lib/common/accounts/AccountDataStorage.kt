package com.owncloud.android.lib.common.accounts

import android.accounts.Account

interface AccountDataStorage {

    fun saveDeviceCertCommonName(deviceCertCommonName: String)

    fun saveTokensToAccount(account: Account)

    fun saveTokensToCurrentAccount()

    fun clearAll()

}