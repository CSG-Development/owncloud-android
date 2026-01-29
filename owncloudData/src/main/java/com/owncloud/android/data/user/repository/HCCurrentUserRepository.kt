package com.owncloud.android.data.user.repository

import com.owncloud.android.data.user.CurrentUserStorage
import com.owncloud.android.domain.user.CurrentUserRepository

class HCCurrentUserRepository(
    private val currentUserStorage: CurrentUserStorage,
): CurrentUserRepository {

    override fun getCurrentUserEmail(): String? {
        return currentUserStorage.getCurrentUserEmail()
    }
}