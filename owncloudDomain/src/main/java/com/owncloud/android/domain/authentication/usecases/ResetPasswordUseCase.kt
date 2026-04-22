package com.owncloud.android.domain.authentication.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.authentication.AuthenticationRepository

class ResetPasswordUseCase(
    private val authenticationRepository: AuthenticationRepository,
) : BaseUseCaseWithResult<Unit, ResetPasswordUseCase.Params>() {

    override fun run(params: Params) {
        require(params.email.isNotEmpty()) { "Invalid email" }
        require(params.serverPath.isNotEmpty()) { "Invalid server path" }
        authenticationRepository.resetPassword(
            serverPath = params.serverPath,
            email = params.email,
        )
    }

    data class Params(
        val serverPath: String,
        val email: String,
    )
}
