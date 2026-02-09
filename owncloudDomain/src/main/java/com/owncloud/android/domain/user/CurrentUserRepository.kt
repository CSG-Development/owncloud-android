package com.owncloud.android.domain.user

interface CurrentUserRepository {

    fun getCurrentUserEmail(): String?
}