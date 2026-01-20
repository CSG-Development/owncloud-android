package com.owncloud.android.presentation.authentication.homecloud

import android.accounts.Account
import android.util.Patterns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.R
import com.owncloud.android.domain.authentication.usecases.LoginBasicAsyncUseCase
import com.owncloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import com.owncloud.android.domain.capabilities.usecases.RefreshCapabilitiesFromServerAsyncUseCase
import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.domain.device.usecases.DynamicUrlSwitchingController
import com.owncloud.android.domain.device.usecases.SaveCurrentDeviceUseCase
import com.owncloud.android.domain.device.usecases.StaticDeviceUseCase
import com.owncloud.android.domain.exceptions.NoNetworkConnectionException
import com.owncloud.android.domain.exceptions.OwncloudVersionNotSupportedException
import com.owncloud.android.domain.exceptions.SSLErrorCode
import com.owncloud.android.domain.exceptions.SSLErrorException
import com.owncloud.android.domain.exceptions.UnauthorizedException
import com.owncloud.android.domain.exceptions.UnknownErrorException
import com.owncloud.android.domain.mdnsdiscovery.usecases.DiscoverLocalNetworkDevicesUseCase
import com.owncloud.android.domain.remoteaccess.usecases.GetExistingRemoteAccessUserUseCase
import com.owncloud.android.domain.remoteaccess.usecases.GetRemoteAccessTokenUseCase
import com.owncloud.android.domain.remoteaccess.usecases.InitiateRemoteAccessAuthenticationUseCase
import com.owncloud.android.domain.server.usecases.GetAvailableDevicesUseCase
import com.owncloud.android.domain.server.usecases.GetAvailableServerInfoUseCase
import com.owncloud.android.domain.spaces.usecases.RefreshSpacesFromServerAsyncUseCase
import com.owncloud.android.extensions.parseError
import com.owncloud.android.lib.common.network.CertificateCombinedException
import com.owncloud.android.presentation.authentication.ACTION_CREATE
import com.owncloud.android.presentation.authentication.EXTRA_ACCOUNT
import com.owncloud.android.presentation.authentication.EXTRA_ACTION
import com.owncloud.android.providers.ContextProvider
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import com.owncloud.android.providers.WorkManagerProvider
import com.owncloud.android.utils.runCatchingException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class LoginViewModel(
    private val loginBasicAsyncUseCase: LoginBasicAsyncUseCase,
    private val refreshCapabilitiesFromServerAsyncUseCase: RefreshCapabilitiesFromServerAsyncUseCase,
    private val getStoredCapabilitiesUseCase: GetStoredCapabilitiesUseCase,
    private val refreshSpacesFromServerAsyncUseCase: RefreshSpacesFromServerAsyncUseCase,
    private val workManagerProvider: WorkManagerProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val contextProvider: ContextProvider,
    private val initiateRemoteAccessAuthenticationUseCase: InitiateRemoteAccessAuthenticationUseCase,
    private val getRemoteAccessTokenUseCase: GetRemoteAccessTokenUseCase,
    private val getServersUseCase: GetAvailableDevicesUseCase,
    private val getExistingRemoteAccessUserUseCase: GetExistingRemoteAccessUserUseCase,
    private val saveCurrentDeviceUseCase: SaveCurrentDeviceUseCase,
    private val dynamicUrlSwitchingController: DynamicUrlSwitchingController,
    private val getAvailableServerInfoUseCase: GetAvailableServerInfoUseCase,
    private val staticDeviceUseCase: StaticDeviceUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow<LoginScreenState>(LoginScreenState.EmailState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events = _events

    private val loginAction by lazy { savedStateHandle.get<Byte>(EXTRA_ACTION) }
    private val account by lazy { savedStateHandle.get<Account>(EXTRA_ACCOUNT) }

    private var serversJob: Job? = null

    init {
        if (loginAction != ACTION_CREATE) {
            account?.let {
                onUserNameChanged(it.name)
            }
        }
        restorePreviousUserIfExists()
    }

    private fun initiateToken() {
        viewModelScope.launch {
            val currentState = _state.value
            runCatchingException(
                block = {
                    when (currentState) {
                        is LoginScreenState.EmailState -> {
                            _state.update {
                                currentState.copy(
                                    devices = emptyList(),
                                    isActionButtonLoading = true
                                )
                            }
                        }

                        is LoginScreenState.LoginState -> {
                            _state.update {
                                currentState.copy(
                                    devices = emptyList(),
                                    isRefreshServersLoading = true
                                )
                            }
                        }
                    }

                    initiateRemoteAccessAuthenticationUseCase.execute(currentState.username)
                },
                exceptionHandlerBlock = { e ->
                    when (currentState) {
                        is LoginScreenState.EmailState -> {
                            _state.update {
                                currentState.copy(errorCodeException = e)
                            }
                        }

                        is LoginScreenState.LoginState -> {
                            _state.update {
                                currentState.copy(
                                    isRefreshServersLoading = false,
                                    isActionButtonLoading = false,
                                    authError = LoginScreenState.AuthError.UnableToDetect
                                )
                            }

                        }
                    }

                },
                completeBlock = { reference ->
                    reference?.let { ref ->
                        _state.update {
                            LoginScreenState.EmailState(
                                username = currentState.username,
                                reference = ref,
                                errorEmailInvalidMessage = null,
                                isActionButtonLoading = false,
                            )
                        }
                    }
                    _events.emit(LoginEvent.ShowCodeDialog)
                    startObserveServers()
                }
            )
        }
    }

    fun onUserNameChanged(username: String) {
        _state.update { currentState ->
            when (currentState) {
                is LoginScreenState.EmailState -> currentState.copy(username = username, errorEmailInvalidMessage = null)
                is LoginScreenState.LoginState -> currentState.copy(username = username)
            }
        }
    }

    fun onPasswordChanged(password: String) {
        _state.update { currentState ->
            when (currentState) {
                is LoginScreenState.LoginState -> currentState.copy(password = password)
                is LoginScreenState.EmailState -> currentState
            }
        }
    }

    fun onDeviceSelected(selectedDevice: Device) {
        changeDevice(selectedDevice)
    }

    fun onServerUrlChanged(serverUrl: String) {
        changeDevice(hostUrl = serverUrl)
    }

    private fun changeDevice(selectedDevice: Device? = null, hostUrl: String = "") {
        _state.update { currentState ->
            when (currentState) {
                is LoginScreenState.LoginState -> {
                    if (selectedDevice == null) {
                        currentState.copy(serverUrl = hostUrl, selectedDevice = null)
                    } else {
                        currentState.copy(selectedDevice = selectedDevice, serverUrl = "")
                    }
                }

                is LoginScreenState.EmailState -> currentState
            }
        }
    }

    fun onCodeEntered(code: String) {
        viewModelScope.launch {
            runCatchingException(
                block = {
                    val currentState = _state.value as LoginScreenState.EmailState
                    _state.update { currentState.copy(isAllowLoading = true, errorCodeException = null) }
                    getRemoteAccessTokenUseCase.execute(currentState.reference, code, currentState.username)
                    switchToLoginState()
                },
                exceptionHandlerBlock = {
                    handleCodeError(it)
                },
                completeBlock = {
                    when (val currentState = _state.value) {
                        is LoginScreenState.EmailState -> _state.update { currentState.copy(isAllowLoading = false) }
                        is LoginScreenState.LoginState -> {} // Already switched
                    }
                    refreshServers()
                }
            )
        }
    }

    private fun handleCodeError(e: Throwable) {
        _state.update { currentState ->
            when (currentState) {
                is LoginScreenState.EmailState -> currentState.copy(errorCodeException = e)
                is LoginScreenState.LoginState -> currentState
            }
        }
    }

    fun onSkipClicked() {
        viewModelScope.launch {
            switchToLoginState()
        }
    }

    fun onBackPressed() {
        viewModelScope.launch {
            when (val currentState = _state.value) {
                is LoginScreenState.LoginState -> {
                    when {
                        currentState.authError != null -> {
                            _state.update {
                                currentState.copy(authError = null)
                            }
                        }

                        else -> {
                            _state.update {
                                LoginScreenState.EmailState(username = currentState.username)
                            }
                        }
                    }
                }

                is LoginScreenState.EmailState -> {
                    _events.emit(LoginEvent.Close)
                }
            }
        }
    }

    private suspend fun switchToLoginState() {
        val currentState = _state.value
        _state.update {
            LoginScreenState.LoginState(
                username = currentState.username,
                devices = currentState.devices
            )
        }
        _events.emit(LoginEvent.DismissCodeDialog)
    }

    private fun restorePreviousUserIfExists() {
        val existingUserName = getExistingRemoteAccessUserUseCase.execute()
        if (existingUserName != null) {
            restorePreviousUser(existingUserName)
        }
    }

    private fun restorePreviousUser(userName: String) {
        onPreviousUserRestore(userName)
        startObserveServers()
        refreshServers()
    }

    private fun onPreviousUserRestore(existingUserName: String) {
        viewModelScope.launch {
            _state.update {
                LoginScreenState.LoginState(
                    username = existingUserName,
                    devices = it.devices
                )
            }
            _events.emit(LoginEvent.DismissCodeDialog)
        }
    }

    private fun startObserveServers() {
        serversJob?.cancel()
        serversJob = viewModelScope.launch {
            getServersUseCase.getServersUpdates(
                this@launch,
                DiscoverLocalNetworkDevicesUseCase.DEFAULT_MDNS_PARAMS
            ).collect { devices ->
                Timber.d("DEBUG devices: $devices")
                _state.update { currentState ->
                    when (currentState) {
                        is LoginScreenState.EmailState -> currentState.copy(
                            devices = devices
                        )

                        is LoginScreenState.LoginState -> currentState.copy(
                            devices = devices,
                            selectedDevice = if (devices.isNotEmpty()) devices.firstOrNull() else currentState.selectedDevice,
                            authError = if (devices.isEmpty()) LoginScreenState.AuthError.UnableToConnect else null
                        )
                    }
                }
            }
        }
    }

    fun refreshServers() {
        viewModelScope.launch {
            runCatchingException(
                block = {
                    _state.update { currentState ->
                        when (currentState) {
                            is LoginScreenState.EmailState -> currentState
                            is LoginScreenState.LoginState -> currentState.copy(isRefreshServersLoading = true, authError = null)
                        }
                    }
                    withContext(coroutinesDispatcherProvider.io) {
                        getServersUseCase.refreshRemoteAccessDevices()
                    }
                },
                exceptionHandlerBlock = {
                    Timber.e(it)
                },
                completeBlock = {
                    _state.update { currentState ->
                        when (currentState) {
                            is LoginScreenState.EmailState -> currentState
                            is LoginScreenState.LoginState -> currentState.copy(
                                isRefreshServersLoading = false,
                                authError = if (currentState.devices.isEmpty()) LoginScreenState.AuthError.UnableToDetect else null
                            )
                        }
                    }
                }
            )

        }
    }

    fun onRetryClicked() {
        val currentState = _state.value
        if (currentState is LoginScreenState.LoginState) {
            when (currentState.authError) {
                is LoginScreenState.AuthError.LoginError -> performLogin()
                LoginScreenState.AuthError.UnableToConnect -> performLogin()
                LoginScreenState.AuthError.UnableToDetect -> refreshServers()
                else -> {}
            }
        }
    }

    fun onCantFindDeviceClicked() {
        val currentState = _state.value
        if (currentState !is LoginScreenState.LoginState) return

        if (getRemoteAccessTokenUseCase.hasToken()) {
            refreshServers()
        } else {
            initiateToken()
        }
    }

    fun onDeveloperOptionsChanged(
        staticDeviceUrl: String,
        isSettingsMenuEnabled: Boolean,
    ) {
        staticDeviceUseCase.execute(staticDeviceUrl)
        _state.update {
            it.copyGeneralState(isSettingsVisible = isSettingsMenuEnabled)
        }
    }

    fun onDeveloperOptionsClicked() {
        viewModelScope.launch {
            val staticDevice = staticDeviceUseCase.getStaticDevice()
            val staticDeviceUrl = staticDevice?.availablePaths?.get(DevicePathType.REMOTE)
            _events.emit(LoginEvent.ShowDeveloperOptions(staticDeviceUrl.orEmpty(), _state.value.isSettingsVisible))
        }
    }

    fun onActionClicked() {
        // Validate email before proceeding
        val currentState = _state.value
        val isEmailValid = Patterns.EMAIL_ADDRESS.matcher(currentState.username).matches()

        when (currentState) {
            is LoginScreenState.EmailState -> {
                if (isEmailValid) {
                    val previousUser = getExistingRemoteAccessUserUseCase.execute()
                    if (currentState.username == previousUser) {
                        restorePreviousUser(previousUser)
                    } else {
                        initiateToken()
                    }
                } else {
                    // Show validation error if somehow the button was clicked with invalid email
                    _state.update {
                        currentState.copy(errorEmailInvalidMessage = contextProvider.getString(R.string.homecloud_login_invalid_email_message))
                    }
                }
            }

            is LoginScreenState.LoginState -> performLogin()
        }
    }

    private fun performLogin() {
        viewModelScope.launch {
            val currentState = _state.value as LoginScreenState.LoginState
            _state.update { currentState.copy(isLoading = true, authError = null) }
            runCatchingException(
                block = {
                    val serverInfoResult = withContext(coroutinesDispatcherProvider.io) {
                        val enforceOIDC = contextProvider.getBoolean(R.bool.enforce_oidc)
                        val secureConnectionEnforced = contextProvider.getBoolean(R.bool.enforce_secure_connection)
                        if (currentState.selectedDevice == null) {
                            getAvailableServerInfoUseCase.getAvailableServerInfo(
                                currentState.serverUrl,
                                enforceOIDC = enforceOIDC,
                                secureConnectionEnforced = secureConnectionEnforced
                            )
                        } else {
                            getAvailableServerInfoUseCase.getAvailableServerInfo(
                                currentState.selectedDevice,
                                enforceOIDC = enforceOIDC,
                                secureConnectionEnforced = secureConnectionEnforced
                            )
                        }
                    }

                    if (serverInfoResult.isSuccess) {
                        val accountNameResult = withContext(coroutinesDispatcherProvider.io) {
                            loginBasicAsyncUseCase(
                                LoginBasicAsyncUseCase.Params(
                                    serverInfo = serverInfoResult.getDataOrNull(),
                                    username = currentState.username,
                                    password = currentState.password,
                                    updateAccountWithUsername = if (loginAction != ACTION_CREATE) account?.name else null
                                )
                            )
                        }

                        if (accountNameResult.isSuccess) {
                            val accountName = accountNameResult.getDataOrNull().orEmpty()
                            dynamicUrlSwitchingController.startDynamicUrlSwitching()
                            discoverAccount(accountName, loginAction == ACTION_CREATE)
                            currentState.selectedDevice?.let { saveCurrentDeviceUseCase(it) }
                            _events.emit(LoginEvent.LoginResult(accountName = accountName))
                        } else {
                            handleLoginError(accountNameResult.getThrowableOrNull())
                        }

                    } else {
                        handleDeviceError()
                    }
                },
                exceptionHandlerBlock = {
                    val state = _state.value as LoginScreenState.LoginState
                    _state.update { state.copy(isLoading = false) }
                },
                completeBlock = {
                }
            )
        }
    }

    private fun handleDeviceError() {
        val state = _state.value
        if (state is LoginScreenState.LoginState) {
            _state.update {
                state.copy(isLoading = false, authError = LoginScreenState.AuthError.UnableToConnect)
            }
        }
    }

    private suspend fun handleLoginError(e: Throwable?) {
        val text = when {
            e is CertificateCombinedException -> {
                _events.emit(LoginEvent.ShowUntrustedCertDialog(e))
                null
            }

            e is OwncloudVersionNotSupportedException -> {
                contextProvider.getString(R.string.server_not_supported)
            }

            e is NoNetworkConnectionException -> {
                contextProvider.getString(R.string.error_no_network_connection)
            }

            e is SSLErrorException && e.code == SSLErrorCode.NOT_HTTP_ALLOWED -> {
                contextProvider.getString(R.string.ssl_connection_not_secure)
            }

            e is UnknownErrorException -> {
                contextProvider.getString(R.string.homecloud_login_server_connection_error)
            }

            e is UnauthorizedException -> {
                contextProvider.getString(R.string.homecloud_login_auth_unauthorized)
            }

            else -> {
                e?.parseError("", contextProvider.getContext().resources)
            }
        }

        _state.update { currentState ->
            when (currentState) {
                is LoginScreenState.LoginState -> currentState.copy(
                    isLoading = false,
                    authError = LoginScreenState.AuthError.LoginError(text?.toString().orEmpty())
                )

                is LoginScreenState.EmailState -> currentState
            }
        }
    }

    fun discoverAccount(accountName: String, discoveryNeeded: Boolean = false) {
        if (!discoveryNeeded) {
            return
        }
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            // 1. Refresh capabilities for account
            refreshCapabilitiesFromServerAsyncUseCase(RefreshCapabilitiesFromServerAsyncUseCase.Params(accountName))
            val capabilities = getStoredCapabilitiesUseCase(GetStoredCapabilitiesUseCase.Params(accountName))

            val spacesAvailableForAccount = capabilities?.isSpacesAllowed() == true

            // 2 If Account does not support spaces we can skip this
            if (spacesAvailableForAccount) {
                refreshSpacesFromServerAsyncUseCase(RefreshSpacesFromServerAsyncUseCase.Params(accountName))
            }
        }
        workManagerProvider.enqueueAccountDiscovery(accountName)
    }

    fun onCodeDialogDismissed() {
        val currentState = _state.value
        if (currentState is LoginScreenState.EmailState) {
            _state.update {
                currentState.copy(errorCodeException = null)
            }
        }
    }

    sealed class LoginScreenState {

        abstract val isSettingsVisible: Boolean

        abstract val username: String

        abstract val devices: List<Device>

        abstract val isActionButtonLoading: Boolean

        fun copyGeneralState(
            isSettingsVisible: Boolean = this.isSettingsVisible,
        ): LoginScreenState {
            return when (this) {
                is LoginState -> copy(isSettingsVisible = isSettingsVisible)
                is EmailState -> copy(isSettingsVisible = isSettingsVisible)
            }
        }

        sealed class AuthError {
            data class LoginError(val errorMessage: String) : AuthError()
            data object UnableToDetect : AuthError()
            data object UnableToConnect : AuthError()
        }

        data class EmailState(
            override val username: String = "",
            val reference: String = "",
            val isAllowLoading: Boolean = false,
            val errorCodeException: Throwable? = null,
            val errorEmailInvalidMessage: String? = null,
            override val devices: List<Device> = emptyList(),
            override val isActionButtonLoading: Boolean = false,
            override val isSettingsVisible: Boolean = false
        ) : LoginScreenState()

        data class LoginState(
            override val username: String = "",
            val password: String = "",
            val isLoading: Boolean = false,
            val isRefreshServersLoading: Boolean = false,
            val authError: AuthError? = null,
            val selectedDevice: Device? = null,
            val serverUrl: String = "",
            override val devices: List<Device> = emptyList(),
            override val isActionButtonLoading: Boolean = false,
            override val isSettingsVisible: Boolean = false
        ) : LoginScreenState()
    }

    sealed class LoginEvent {
        data object ShowCodeDialog : LoginEvent()

        data class ShowDeveloperOptions(val staticDeviceUrl: String, val isSettingsMenuEnabled: Boolean = false): LoginEvent()
        data object DismissCodeDialog : LoginEvent()

        data object Close : LoginEvent()

        data class LoginResult(val accountName: String, val error: String? = null) : LoginEvent()

        data class ShowUntrustedCertDialog(val certificateCombinedException: CertificateCombinedException) : LoginEvent()
    }
}