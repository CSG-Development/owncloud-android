package com.owncloud.android.presentation.authentication.homecloud

import android.accounts.AccountManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.util.Patterns
import android.view.MotionEvent
import android.view.View
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.owncloud.android.R
import com.owncloud.android.databinding.AccountDialogDeveloperOptionsBinding
import com.owncloud.android.databinding.AccountSetupHomecloudBinding
import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.extensions.applyStatusBarInsets
import com.owncloud.android.extensions.checkPasscodeEnforced
import com.owncloud.android.extensions.getAppName
import com.owncloud.android.extensions.manageOptionLockSelected
import com.owncloud.android.extensions.showMessageInSnackbar
import com.owncloud.android.extensions.updateTextIfDiffers
import com.owncloud.android.lib.common.network.CertificateCombinedException
import com.owncloud.android.presentation.authentication.AccountUtils.getAccounts
import com.owncloud.android.presentation.authentication.UNTRUSTED_CERT_DIALOG_TAG
import com.owncloud.android.presentation.authentication.homecloud.LoginViewModel.LoginScreenState
import com.owncloud.android.presentation.security.LockType
import com.owncloud.android.presentation.security.SecurityEnforced
import com.owncloud.android.presentation.settings.SettingsActivity
import com.owncloud.android.ui.activity.FileDisplayActivity
import com.owncloud.android.ui.custom.CustomAutoCompleteTextView
import com.owncloud.android.ui.custom.LoadingButton
import com.owncloud.android.ui.custom.getTypedData
import com.owncloud.android.ui.dialog.SslUntrustedCertDialog
import com.owncloud.android.utils.PreferenceUtils
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class LoginActivity : AppCompatActivity(), SslUntrustedCertDialog.OnSslUntrustedCertListener, SecurityEnforced {

    private val loginViewModel by viewModel<LoginViewModel>()

    private lateinit var binding: AccountSetupHomecloudBinding
    private val developerOptionsDialogBinding by lazy { AccountDialogDeveloperOptionsBinding.inflate(layoutInflater) }

    // Two-finger tap detection for developer options
    private var twoFingerTapCount = 0
    private var lastTwoFingerTapTime = 0L
    private var maxPointersDuringGesture = 0

    private companion object {
        private const val SUPPORT_LINK = "https://www.seagate.com/es/es/support/"
        private const val TWO_FINGER_TAP_TIMEOUT_MS = 2000L
        private const val REQUIRED_TAP_COUNT = 5
    }

    private val unableToDetectMessage: SpannableStringBuilder by lazy {
        createUnableToDetectMessage()
    }

    private val developerOptionsDialog by lazy {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setView(developerOptionsDialogBinding.root)
            .create()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPasscodeEnforced(this)
        enableEdgeToEdge()

        handleDeepLink()

        onBackPressedDispatcher.addCallback {
            loginViewModel.onBackPressed()
        }

        binding = AccountSetupHomecloudBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.thumbnailName.text = getAppName()
        binding.settingsLink.applyStatusBarInsets(usePaddings = false)
        binding.backButton.applyStatusBarInsets(usePaddings = false)
        binding.root.filterTouchesWhenObscured =
            PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(this)

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED, block = {
                launch {
                    loginViewModel.state.collect {
                        updateLoginState(it)
                    }
                }
                launch {
                    loginViewModel.events.collect {
                        handleEvents(it)
                    }
                }
            })
        }
        setupListeners()
    }

    private fun handleDeepLink() {
        if (intent.data != null) {
            if (getAccounts(baseContext).isNotEmpty()) {
                launchFileDisplayActivity()
            } else {
                showMessageInSnackbar(message = baseContext.getString(R.string.uploader_wrn_no_account_title))
            }
        }
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener {
            loginViewModel.onBackPressed()
        }
        binding.settingsLink.setOnClickListener {
            val settingsIntent = Intent(this, SettingsActivity::class.java)
            startActivity(settingsIntent)
        }
        setupTwoFingerTapDetection()
        binding.accountUsername.doAfterTextChanged { text ->
            loginViewModel.onUserNameChanged(text.toString())
        }
        binding.accountPassword.doAfterTextChanged { text ->
            loginViewModel.onPasswordChanged(text.toString())
        }
        binding.resetPasswordLink.setOnClickListener {
            showMessageInSnackbar(message = "Not implemented yet")
        }
        binding.cantFindDevice.setOnClickListener {
            loginViewModel.onCantFindDeviceClicked()
        }
        binding.actionButton.setOnClickListener {
            loginViewModel.onActionClicked()
        }

        binding.hostUrlInput.setOnItemSelectedListener { item, _ ->
            item.getTypedData<Device>()?.let { device ->
                loginViewModel.onDeviceSelected(device)
            }
        }
        // Show dropdown when clicking on the TextInputLayout end icon
        binding.hostUrlInputLayout.setEndIconOnClickListener {
            binding.hostUrlInput.toggleDropdown()
        }
        binding.serversRefreshButton.setOnClickListener {
            loginViewModel.refreshServers()
        }

        binding.unableToConnectLayout.unableToConnectBackButton.setOnClickListener {
            loginViewModel.onBackPressed()
        }
        binding.unableToConnectLayout.unableToConnectRetryButton.setOnClickListener {
            loginViewModel.onRetryClicked()
        }
    }

    //TODO: The styling of description and text is a subject to change in nearest future. To be defined....
    private fun createUnableToConnectMessage(): SpannableStringBuilder {
        val linkColor = ContextCompat.getColor(this, R.color.homecloud_color_accent)
        val description = getString(R.string.homecloud_unable_to_connect_description)
        val items = listOf(
            getString(R.string.homecloud_unable_to_connect_item_1),
            getString(R.string.homecloud_unable_to_connect_item_2),
            getString(R.string.homecloud_unable_to_connect_item_3),
            getString(R.string.homecloud_unable_to_connect_item_4),
            getString(R.string.homecloud_unable_to_connect_item_5),
            getString(R.string.homecloud_unable_to_connect_item_6)
        )
        val supportPrefix = getString(R.string.homecloud_unable_to_connect_support)
        val supportLink = getString(R.string.homecloud_unable_to_connect_support_link)
        val supportSuffix = getString(R.string.homecloud_unable_to_connect_support_suffix)

        val builder = SpannableStringBuilder()
        builder.append(description)

        val numberIndent = resources.getDimensionPixelSize(R.dimen.standard_padding)
        val textView = binding.unableToConnectLayout.unableToConnectContent
        val paint = textView.paint

        items.forEachIndexed { index, item ->
            builder.append("\n\n")
            val itemStart = builder.length
            val numberText = "${index + 1}. "
            builder.append(numberText)
            builder.append(item)
            val itemEnd = builder.length

            val numberTextWidth = paint.measureText(numberText).toInt()
            val textIndent = numberIndent + numberTextWidth

            builder.setSpan(
                LeadingMarginSpan.Standard(numberIndent, textIndent),
                itemStart,
                itemEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Add support text with clickable link
        builder.append("\n\n")
        builder.append(supportPrefix)
        builder.append(" ")

        val linkStart = builder.length
        builder.append(supportLink)
        builder.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_LINK))
                startActivity(intent)
            }
        }, linkStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(ForegroundColorSpan(linkColor), linkStart, builder.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)

        builder.append(" ")
        builder.append(supportSuffix)
        return builder
    }

    private fun createUnableToDetectMessage(): SpannableStringBuilder {
        // TODO: temporary the same as unable to connect
        return createUnableToConnectMessage()
    }

    private fun setupUnableToDetectContent() {
        binding.unableToConnectLayout.unableToConnectTitle.text = getString(R.string.homecloud_unable_to_detect_title)
        binding.unableToConnectLayout.unableToConnectContent.text = unableToDetectMessage
        binding.unableToConnectLayout.unableToConnectContent.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setupUnableToConnectContent() {
        binding.unableToConnectLayout.unableToConnectTitle.text = getString(R.string.homecloud_unable_to_connect_title)
        binding.unableToConnectLayout.unableToConnectContent.text = unableToDetectMessage
        binding.unableToConnectLayout.unableToConnectContent.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun handleEvents(event: LoginViewModel.LoginEvent) {
        when (event) {
            is LoginViewModel.LoginEvent.ShowCodeDialog -> showCodeDialog(event.email)
            is LoginViewModel.LoginEvent.LoginResult -> handleLoginResult(event)
            is LoginViewModel.LoginEvent.ShowUntrustedCertDialog -> showUntrustedCertDialog(event.certificateCombinedException)
            LoginViewModel.LoginEvent.Close -> finish()
            is LoginViewModel.LoginEvent.ShowDeveloperOptions -> showDeveloperOptionsDialog(event.staticDeviceUrl, event.isSettingsMenuEnabled)
        }
    }

    private fun showUntrustedCertDialog(certificateCombinedException: CertificateCombinedException) {
        val dialog = SslUntrustedCertDialog.newInstanceForFullSslError(certificateCombinedException)
        val fm = supportFragmentManager
        val ft = fm.beginTransaction()
        ft.addToBackStack(null)
        dialog.show(ft, UNTRUSTED_CERT_DIALOG_TAG)
    }

    private fun handleLoginResult(result: LoginViewModel.LoginEvent.LoginResult) {
        val intent = Intent()
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, result.accountName)
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.account_type))
        setResult(RESULT_OK, intent)
        launchFileDisplayActivity()
    }

    private fun updateDevices(devices: List<Device>, selectedDevice: Device?) {
        val deviceIcon = ContextCompat.getDrawable(this, R.drawable.ic_device)
        val dropdownItems = devices.map { device ->
            CustomAutoCompleteTextView.DropdownItem(
                id = device.id,
                text = device.name,
                isSelected = device.id == selectedDevice?.id,
                data = device
            )
        }.toMutableList()

        binding.hostUrlInput.setDropdownItems(dropdownItems)
        binding.hostUrlInputLayout.startIconDrawable = if (devices.isEmpty()) null else deviceIcon
    }

    private fun showCodeDialog(email: String) {
        VerificationCodeDialogFragment.newInstance(email)
            .setListener(object : VerificationCodeDialogFragment.VerificationCodeDialogListener {
                override fun onCodeVerified() {
                    loginViewModel.onRemoteAccessVerified()
                }

                override fun onSkipped() {
                    loginViewModel.onRemoteAccessSkipped()
                }

                override fun onDismissed(lastError: VerificationCodeViewModel.VerificationCodeError?) {
                    if (lastError != null) {
                        loginViewModel.onRemoteAccessError(lastError)
                    }
                }
            })
            .show(supportFragmentManager, VerificationCodeDialogFragment.TAG)
    }

    private fun updateLoginState(state: LoginScreenState) {
        binding.settingsLink.isVisible = state.isSettingsVisible
        setSelectedDevice(state.selectedDevice)
        when (state) {
            is LoginScreenState.EmailState -> {
                // Show main scroll view, hide unable to connect
                binding.scrollView.visibility = View.VISIBLE
                binding.unableToConnectLayout.unableToConnectContainer.visibility = View.GONE

                // Show email input, hide email text
                binding.accountUsernameContainer.visibility = View.VISIBLE
                binding.accountUsernameText.visibility = View.GONE
                binding.accountUsername.updateTextIfDiffers(state.username)

                binding.backButton.visibility = View.GONE
                binding.accountUsernameContainer.error = state.errorEmailInvalidMessage
                binding.loginStateGroup.visibility = View.GONE
                binding.actionButton.setText(R.string.homecloud_action_button_next)
                // Enable button only if username is not empty and is a valid email
                if (state.isActionButtonLoading) {
                    binding.actionButton.setState(LoadingButton.State.LOADING)
                } else {
                    val state =
                        if (state.username.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(state.username).matches()) {
                            LoadingButton.State.ENABLED
                        } else {
                            LoadingButton.State.DISABLED
                        }
                    binding.actionButton.setState(state)
                }

                binding.serversRefreshButton.visibility = View.INVISIBLE
                binding.serversRefreshLoading.visibility = View.GONE
                binding.accountUsername.isEnabled = true
            }

            is LoginScreenState.LoginState -> {
                changeLoadingState(state)
                if (state.authError != null) {
                    when (state.authError) {
                        is LoginScreenState.AuthError.LoginError -> {
                            binding.errorMessage.visibility = View.VISIBLE
                            binding.errorMessage.text = state.authError.errorMessage
                        }

                        LoginScreenState.AuthError.UnableToConnect -> {
                            binding.unableToConnectLayout.unableToConnectContainer.visibility = View.VISIBLE
                            binding.scrollView.visibility = View.GONE
                            binding.backButton.visibility = View.GONE
                            setupUnableToConnectContent()
                        }

                        LoginScreenState.AuthError.UnableToDetect -> {
                            binding.unableToConnectLayout.unableToConnectContainer.visibility = View.VISIBLE
                            binding.scrollView.visibility = View.GONE
                            binding.backButton.visibility = View.GONE
                            setupUnableToDetectContent()
                        }
                    }
                } else {
                    // Show main scroll view, hide unable to connect
                    binding.scrollView.visibility = View.VISIBLE
                    binding.unableToConnectLayout.unableToConnectContainer.visibility = View.GONE

                    // Hide email input, show email text
                    binding.accountUsernameContainer.visibility = View.GONE
                    binding.accountUsernameText.text = state.username

                    binding.backButton.visibility = View.VISIBLE
                    updateDevices(state.devices, state.selectedDevice)
                    binding.accountPassword.updateTextIfDiffers(state.password)
                }
            }
        }
    }

    private fun setSelectedDevice(selectedDevice: Device?) {
        val selectedDevice = selectedDevice
        if (selectedDevice != null) {
            binding.hostUrlInput.updateTextIfDiffers(selectedDevice.name)
        }
    }

    private fun changeLoadingState(state: LoginScreenState.LoginState) {
        if (state.isLoading) {
            binding.accountUsernameText.visibility = View.GONE
            binding.errorMessage.visibility = View.GONE
            binding.serversRefreshButton.visibility = View.INVISIBLE
            binding.serversRefreshLoading.visibility = View.GONE
            binding.backButton.visibility = View.GONE
            binding.loadingLayout.visibility = View.VISIBLE
            binding.actionGroup.visibility = View.GONE
            binding.loginStateGroup.visibility = View.GONE
        } else {
            binding.accountUsernameText.visibility = View.VISIBLE
            binding.serversRefreshButton.visibility = if (state.isRefreshServersLoading) View.INVISIBLE else View.VISIBLE
            binding.serversRefreshLoading.visibility = if (state.isRefreshServersLoading) View.VISIBLE else View.GONE
            binding.backButton.visibility = View.VISIBLE
            binding.loadingLayout.visibility = View.GONE
            binding.actionGroup.visibility = View.VISIBLE
            binding.loginStateGroup.visibility = View.VISIBLE
            binding.actionButton.setText(R.string.setup_btn_login)
            if (state.isActionButtonLoading) {
                binding.actionButton.setState(LoadingButton.State.LOADING)
            } else {
                val state =
                    if (state.username.isNotEmpty() && state.password.isNotEmpty() &&
                        state.selectedDevice != null &&
                        Patterns.EMAIL_ADDRESS.matcher(state.username)
                            .matches()
                    ) {
                        LoadingButton.State.ENABLED
                    } else {
                        LoadingButton.State.DISABLED
                    }
                binding.actionButton.setState(state)
            }
        }
    }

    private fun launchFileDisplayActivity() {
        val newIntent = Intent(this, FileDisplayActivity::class.java)
        newIntent.data = intent.data
        newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(newIntent)
        finish()
    }

    override fun onSavedCertificate() {
        loginViewModel.onActionClicked()
    }

    override fun onFailedSavingCertificate() {

    }

    override fun onCancelCertificate() {

    }

    override fun optionLockSelected(type: LockType) {
        manageOptionLockSelected(type)
    }

    @Suppress("ClickableViewAccessibility")
    private fun setupTwoFingerTapDetection() {
        binding.thumbnailLogo.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Reset max pointers when a new gesture starts
                    maxPointersDuringGesture = 1
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Track max number of pointers during this gesture
                    maxPointersDuringGesture = maxOf(maxPointersDuringGesture, event.pointerCount)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Check if we had 2 fingers at any point during this gesture
                    if (maxPointersDuringGesture >= 2) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTwoFingerTapTime > TWO_FINGER_TAP_TIMEOUT_MS) {
                            twoFingerTapCount = 0
                        }
                        twoFingerTapCount++
                        lastTwoFingerTapTime = currentTime

                        if (twoFingerTapCount >= REQUIRED_TAP_COUNT) {
                            twoFingerTapCount = 0
                            loginViewModel.onDeveloperOptionsClicked()
                        }
                    }
                    maxPointersDuringGesture = 0
                    true
                }
                else -> false
            }
        }
    }

    private fun showDeveloperOptionsDialog(
        currentStaticDeviceUrl: String,
        isSettingsMenuEnabled: Boolean
    ) {
        developerOptionsDialogBinding.staticDeviceInput.setText(currentStaticDeviceUrl)
        developerOptionsDialogBinding.menuButtonSwitch.isChecked = isSettingsMenuEnabled
        developerOptionsDialogBinding.cancelButton.setOnClickListener {
            developerOptionsDialog.dismiss()
        }
        developerOptionsDialogBinding.okButton.setOnClickListener {
            val staticDeviceUrl = developerOptionsDialogBinding.staticDeviceInput.text.toString()
            loginViewModel.onDeveloperOptionsChanged(
                staticDeviceUrl,
                developerOptionsDialogBinding.menuButtonSwitch.isChecked
            )
            developerOptionsDialog.dismiss()
        }
        developerOptionsDialog.show()
    }
}