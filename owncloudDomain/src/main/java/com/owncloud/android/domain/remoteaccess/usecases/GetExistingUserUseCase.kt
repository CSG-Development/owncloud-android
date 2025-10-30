package com.owncloud.android.domain.remoteaccess.usecases

import com.owncloud.android.domain.remoteaccess.RemoteAccessRepository

class GetExistingUserUseCase(
    private val remoteAccessRepository: RemoteAccessRepository
) {

    fun execute(): String? = remoteAccessRepository.getUserName()
}