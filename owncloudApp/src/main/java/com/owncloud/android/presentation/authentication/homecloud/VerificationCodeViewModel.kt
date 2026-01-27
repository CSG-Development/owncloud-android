package com.owncloud.android.presentation.authentication.homecloud

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.domain.exceptions.CodeExpiredException
import com.owncloud.android.domain.exceptions.EmailNotRegisteredException
import com.owncloud.android.domain.exceptions.ServerTooManyRequestsException
import com.owncloud.android.domain.exceptions.ServiceUnavailableException
import com.owncloud.android.domain.exceptions.WrongCodeException
import com.owncloud.android.domain.remoteaccess.usecases.GetRemoteAccessTokenUseCase
import com.owncloud.android.domain.remoteaccess.usecases.InitiateRemoteAccessAuthenticationUseCase
import com.owncloud.android.utils.runCatchingException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VerificationCodeViewModel(
    private val initiateRemoteAccessAuthenticationUseCase: InitiateRemoteAccessAuthenticationUseCase,
    private val getRemoteAccessTokenUseCase: GetRemoteAccessTokenUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(VerificationCodeState())
    val state: StateFlow<VerificationCodeState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<VerificationCodeEvent>()
    val events: SharedFlow<VerificationCodeEvent> = _events.asSharedFlow()

    private val email: String by lazy { savedStateHandle.get<String>(ARG_EMAIL).orEmpty() }
    private var reference: String = ""

    companion object {
        private const val ARG_EMAIL = "email"
    }

    init {
        initiateAuthentication()
    }

    /**
     * Initiates the remote access authentication process.
     * This sends an email with verification code to the user.
     */
    fun initiateAuthentication() {
        if (email.isEmpty()) return

        viewModelScope.launch {
            runCatchingException(
                block = {
                    _state.update { it.copy(isInitiating = true, error = null) }
                    reference = initiateRemoteAccessAuthenticationUseCase.execute(email)
                    _state.update { it.copy(isInitiating = false, isCodeSent = true) }
                },
                exceptionHandlerBlock = { e ->
                    handleCodeError(e)
                }
            )
        }
    }

    /**
     * Called when the user enters a verification code.
     */
    fun onCodeEntered(code: String) {
        if (code.isEmpty() || reference.isEmpty()) return

        viewModelScope.launch {
            runCatchingException(
                block = {
                    _state.update { it.copy(isVerifying = true, error = null) }
                    getRemoteAccessTokenUseCase.execute(reference, code, email)
                    _state.update { it.copy(isVerifying = false) }
                    _events.emit(VerificationCodeEvent.CodeVerified)
                },
                exceptionHandlerBlock = { e ->
                    handleCodeError(e)
                }
            )
        }
    }

    private fun handleCodeError(e: Exception) {
        val error = when (e) {
            is WrongCodeException -> VerificationCodeError.WrongCode
            is CodeExpiredException -> VerificationCodeError.CodeExpired
            is ServerTooManyRequestsException -> VerificationCodeError.TooManyRequests
            is ServiceUnavailableException -> VerificationCodeError.ServiceUnavailable
            is EmailNotRegisteredException -> VerificationCodeError.EmailNotRegistered
            else -> VerificationCodeError.UnknownError(e)
        }
        _state.update { it.copy(isVerifying = false, error = error) }
    }

    /**
     * Called when the user wants to resend the verification code.
     */
    fun onResendClicked() {
        initiateAuthentication()
    }

    /**
     * Called when the user wants to skip verification.
     */
    fun onSkipClicked() {
        viewModelScope.launch {
            _events.emit(VerificationCodeEvent.Skipped)
        }
    }

    /**
     * Clears the current error state.
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * State for the verification code dialog.
     */
    data class VerificationCodeState(
        val isInitiating: Boolean = false,
        val isVerifying: Boolean = false,
        val isCodeSent: Boolean = false,
        val error: VerificationCodeError? = null,
    ) {
        val isLoading: Boolean get() = isInitiating || isVerifying
        val isAllowButtonEnabled: Boolean get() = !isLoading && error !is VerificationCodeError.WrongCode
    }

    /**
     * Error types for verification code dialog.
     */
    sealed class VerificationCodeError {
        data object WrongCode : VerificationCodeError()
        data object CodeExpired : VerificationCodeError()

        data object TooManyRequests : VerificationCodeError()

        data object ServiceUnavailable : VerificationCodeError()

        data object EmailNotRegistered : VerificationCodeError()


        data class UnknownError(val exception: Exception) : VerificationCodeError()
    }

    /**
     * Events emitted by the verification code dialog.
     */
    sealed class VerificationCodeEvent {
        /**
         * Emitted when the code is verified successfully.
         */
        data object CodeVerified : VerificationCodeEvent()

        /**
         * Emitted when the user skips verification.
         */
        data object Skipped : VerificationCodeEvent()
    }
}
