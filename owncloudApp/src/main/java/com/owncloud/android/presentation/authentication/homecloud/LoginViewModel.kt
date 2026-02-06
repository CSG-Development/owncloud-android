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
import com.owncloud.android.domain.device.usecases.GetStaticDeviceUseCase
import com.owncloud.android.domain.device.usecases.SaveCurrentDeviceUseCase
import com.owncloud.android.domain.device.usecases.SaveStaticDeviceUseCase
import com.owncloud.android.domain.exceptions.NoNetworkConnectionException
import com.owncloud.android.domain.exceptions.OwncloudVersionNotSupportedException
import com.owncloud.android.domain.exceptions.SSLErrorCode
import com.owncloud.android.domain.exceptions.SSLErrorException
import com.owncloud.android.domain.exceptions.UnauthorizedException
import com.owncloud.android.domain.exceptions.UnknownErrorException
import com.owncloud.android.domain.mdnsdiscovery.usecases.DiscoverLocalNetworkDevicesUseCase
import com.owncloud.android.domain.remoteaccess.usecases.GetExistingRemoteAccessUserUseCase
import com.owncloud.android.domain.remoteaccess.usecases.GetRemoteAccessTokenUseCase
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
    private val getRemoteAccessTokenUseCase: GetRemoteAccessTokenUseCase,
    private val getServersUseCase: GetAvailableDevicesUseCase,
    private val discoverLocalNetworkDevicesUseCase: DiscoverLocalNetworkDevicesUseCase,
    private val getExistingRemoteAccessUserUseCase: GetExistingRemoteAccessUserUseCase,
    private val saveCurrentDeviceUseCase: SaveCurrentDeviceUseCase,
    private val dynamicUrlSwitchingController: DynamicUrlSwitchingController,
    private val getAvailableServerInfoUseCase: GetAvailableServerInfoUseCase,
    private val saveStaticDeviceUseCase: SaveStaticDeviceUseCase,
    private val getStaticDeviceUseCase: GetStaticDeviceUseCase,
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

    private fun showRemoteAccessCodeDialog() {
        viewModelScope.launch {
            _events.emit(LoginEvent.ShowCodeDialog(_state.value.username))
            startObserveServers()
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
        _state.update { currentState ->
            currentState.copyGeneralState(selectedDevice = selectedDevice)
        }
    }

    fun onRemoteAccessVerified() {
        viewModelScope.launch {
            switchToLoginState()
            refreshServers()
        }
    }

    fun onRemoteAccessSkipped() {
        switchToLoginState()
        val currentState = _state.value
        if (currentState is LoginScreenState.LoginState && currentState.devices.isEmpty()) {
            _state.update {
                currentState.copy(authError = LoginScreenState.AuthError.UnableToDetect)
            }
        }
    }

    private fun checkMdns() {
        viewModelScope.launch {
            _state.update { currentState -> currentState.copyGeneralState(isActionButtonLoading = true) }
            val device = withContext(coroutinesDispatcherProvider.io) {
                discoverLocalNetworkDevicesUseCase.oneShot()
            }
            if (device == null) {
                showRemoteAccessCodeDialog()
            } else {
                switchToLoginState(device)
                startObserveServers()
            }
            _state.update { currentState -> currentState.copyGeneralState(isActionButtonLoading = false) }
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

    private fun switchToLoginState(device: Device? = null) {
        val currentState = _state.value
        _state.update {
            LoginScreenState.LoginState(
                username = currentState.username,
                devices = if (device == null) currentState.devices else listOf(device),
                selectedDevice = device ?: currentState.selectedDevice,
                isSettingsVisible = currentState.isSettingsVisible
            )
        }
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
        _state.update {
            LoginScreenState.LoginState(
                username = existingUserName,
                devices = it.devices,
                selectedDevice = it.selectedDevice,
                isSettingsVisible = it.isSettingsVisible
            )
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
                    val selectedDevice = if (devices.isNotEmpty()) devices.firstOrNull() else currentState.selectedDevice
                    currentState.copyGeneralState(
                        devices = devices,
                        selectedDevice = selectedDevice
                    )
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
            showRemoteAccessCodeDialog()
        }
    }

    fun onDeveloperOptionsChanged(
        staticDeviceUrl: String,
        isSettingsMenuEnabled: Boolean,
    ) {
        saveStaticDeviceUseCase.execute(staticDeviceUrl)
        _state.update {
            it.copyGeneralState(isSettingsVisible = isSettingsMenuEnabled)
        }
    }

    fun onDeveloperOptionsClicked() {
        viewModelScope.launch {
            val staticDevice = getStaticDeviceUseCase.execute()
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
                        checkMdns()
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
            val selectedDevice = currentState.selectedDevice
            if (selectedDevice != null) {
                _state.update { currentState.copy(isLoading = true, authError = null) }
                runCatchingException(
                    block = {
                        val serverInfoResult = withContext(coroutinesDispatcherProvider.io) {
                            val enforceOIDC = contextProvider.getBoolean(R.bool.enforce_oidc)
                            val secureConnectionEnforced = contextProvider.getBoolean(R.bool.enforce_secure_connection)
                            getAvailableServerInfoUseCase.getAvailableServerInfo(
                                selectedDevice,
                                enforceOIDC = enforceOIDC,
                                secureConnectionEnforced = secureConnectionEnforced
                            )
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
                                dynamicUrlSwitchingController.startDynamicUrlSwitching(false)
                                discoverAccount(accountName, loginAction == ACTION_CREATE)
                                saveCurrentDeviceUseCase(selectedDevice)
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

    fun onRemoteAccessError(lastError: VerificationCodeViewModel.VerificationCodeError) {
        when (lastError) {
            is VerificationCodeViewModel.VerificationCodeError.EmailNotRegistered -> {
                _state.update { currentState ->
                    LoginScreenState.EmailState(
                        username = currentState.username,
                        errorEmailInvalidMessage = contextProvider.getString(R.string.homecloud_login_email_not_allowed)
                    )
                }
            }

            else -> {}
        }
    }

    sealed class LoginScreenState {

        abstract val isSettingsVisible: Boolean

        abstract val username: String

        abstract val devices: List<Device>

        abstract val selectedDevice: Device?

        abstract val isActionButtonLoading: Boolean

        fun copyGeneralState(
            isSettingsVisible: Boolean = this.isSettingsVisible,
            username: String = this.username,
            devices: List<Device> = this.devices,
            selectedDevice: Device? = this.selectedDevice,
            isActionButtonLoading: Boolean = this.isActionButtonLoading
        ): LoginScreenState {
            return when (this) {
                is LoginState -> copy(
                    isSettingsVisible = isSettingsVisible,
                    username = username,
                    devices = devices,
                    selectedDevice = selectedDevice,
                    isActionButtonLoading = isActionButtonLoading
                )

                is EmailState -> copy(
                    isSettingsVisible = isSettingsVisible,
                    username = username,
                    devices = devices,
                    selectedDevice = selectedDevice,
                    isActionButtonLoading = isActionButtonLoading
                )
            }
        }

        sealed class AuthError {
            data class LoginError(val errorMessage: String) : AuthError()
            data object UnableToDetect : AuthError()
            data object UnableToConnect : AuthError()
        }

        data class EmailState(
            override val username: String = "",
            val errorEmailInvalidMessage: String? = null,
            override val devices: List<Device> = emptyList(),
            override val selectedDevice: Device? = null,
            override val isActionButtonLoading: Boolean = false,
            override val isSettingsVisible: Boolean = false,
        ) : LoginScreenState()

        data class LoginState(
            override val username: String = "",
            val password: String = "",
            val isLoading: Boolean = false,
            val isRefreshServersLoading: Boolean = false,
            val authError: AuthError? = null,
            override val devices: List<Device> = emptyList(),
            override val selectedDevice: Device? = null,
            override val isActionButtonLoading: Boolean = false,
            override val isSettingsVisible: Boolean = false,
        ) : LoginScreenState()
    }

    sealed class LoginEvent {
        data class ShowCodeDialog(val email: String) : LoginEvent()

        data class ShowDeveloperOptions(val staticDeviceUrl: String, val isSettingsMenuEnabled: Boolean = false) : LoginEvent()

        data object Close : LoginEvent()

        data class LoginResult(val accountName: String, val error: String? = null) : LoginEvent()

        data class ShowUntrustedCertDialog(val certificateCombinedException: CertificateCombinedException) : LoginEvent()
    }
}