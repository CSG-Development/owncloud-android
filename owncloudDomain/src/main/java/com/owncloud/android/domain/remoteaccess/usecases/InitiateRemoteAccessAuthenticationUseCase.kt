package com.owncloud.android.domain.remoteaccess.usecases

import android.os.Build
import com.owncloud.android.domain.GetFirebaseInstallationIdUseCase
import com.owncloud.android.domain.remoteaccess.RemoteAccessRepository

class InitiateRemoteAccessAuthenticationUseCase(
    private val remoteAccessRepository: RemoteAccessRepository,
    private val firebaseInstallationIdUseCase: GetFirebaseInstallationIdUseCase,
) {
    suspend fun execute(
        email: String,
    ): String =
        remoteAccessRepository.initiateAuthentication(
            email = email,
            clientId = firebaseInstallationIdUseCase.getInstallationId(),
            clientFriendlyName = Build.MODEL
        )
}

