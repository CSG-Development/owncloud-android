package com.owncloud.android.data.user

import com.owncloud.android.data.authentication.SELECTED_ACCOUNT
import com.owncloud.android.data.providers.SharedPreferencesProvider

class CurrentUserStorage(
    private val sharedPreferencesProvider: SharedPreferencesProvider
) {

    fun getCurrentUserEmail(): String? {
        val accountName =  sharedPreferencesProvider.getString(SELECTED_ACCOUNT, null)
        return accountName?.substring(0, accountName.lastIndexOf("@"))
    }
}