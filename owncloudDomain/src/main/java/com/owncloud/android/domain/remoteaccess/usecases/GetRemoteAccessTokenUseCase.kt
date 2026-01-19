package com.owncloud.android.domain.remoteaccess.usecases

import com.owncloud.android.domain.GetFirebaseInstallationIdUseCase
import com.owncloud.android.domain.remoteaccess.RemoteAccessRepository

class GetRemoteAccessTokenUseCase(
    private val remoteAccessRepository: RemoteAccessRepository,
    private val getFirebaseInstallationIdUseCase: GetFirebaseInstallationIdUseCase,
) {

    suspend fun execute(reference: String, code: String, username: String) =
        remoteAccessRepository.getToken(
            reference = reference,
            code = code,
            userName = username,
            clientId = getFirebaseInstallationIdUseCase.getInstallationId()
        )

    fun hasToken(): Boolean = remoteAccessRepository.hasAccessToken()
}

