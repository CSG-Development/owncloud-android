package com.owncloud.android.domain.remoteaccess.usecases

import com.owncloud.android.domain.user.CurrentUserRepository

class GetExistingRemoteAccessUserUseCase(
    private val currentUserRepository: CurrentUserRepository
) {

    fun execute(): String? = currentUserRepository.getCurrentUserEmail()
}