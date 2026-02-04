/**
 * ownCloud Android client application
 *
 * @author Abel García de Prada
 *
 * Copyright (C) 2020 ownCloud GmbH.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.owncloud.android.ui.activity

import android.accounts.AccountManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.owncloud.android.BuildConfig
import com.owncloud.android.MainApp
import com.owncloud.android.R
import com.owncloud.android.data.providers.SharedPreferencesProvider
import com.owncloud.android.data.providers.implementation.OCSharedPreferencesProvider
import com.owncloud.android.extensions.collectLatestLifecycleFlow
import com.owncloud.android.presentation.appupdate.AppUpdateDialogFragment
import com.owncloud.android.presentation.appupdate.AppUpdateState
import com.owncloud.android.presentation.appupdate.AppUpdateViewModel
import com.owncloud.android.presentation.authentication.homecloud.LoginActivity
import com.owncloud.android.presentation.security.LockTimeout
import com.owncloud.android.presentation.security.PREFERENCE_LOCK_TIMEOUT
import com.owncloud.android.providers.MdmProvider
import com.owncloud.android.ui.activity.FileDisplayActivity.Companion.PREFERENCE_CLEAR_DATA_ALREADY_TRIGGERED
import com.owncloud.android.utils.CONFIGURATION_ALLOW_SCREENSHOTS
import com.owncloud.android.utils.CONFIGURATION_DEVICE_PROTECTION
import com.owncloud.android.utils.CONFIGURATION_LOCK_DELAY_TIME
import com.owncloud.android.utils.CONFIGURATION_OAUTH2_OPEN_ID_PROMPT
import com.owncloud.android.utils.CONFIGURATION_OAUTH2_OPEN_ID_SCOPE
import com.owncloud.android.utils.CONFIGURATION_REDACT_AUTH_HEADER_LOGS
import com.owncloud.android.utils.CONFIGURATION_SEND_LOGIN_HINT_AND_USER
import com.owncloud.android.utils.CONFIGURATION_SERVER_URL
import com.owncloud.android.utils.CONFIGURATION_SERVER_URL_INPUT_VISIBILITY
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class SplashActivity : AppCompatActivity(), AppUpdateDialogFragment.AppUpdateDialogListener {

    private val sharedPreferences: SharedPreferencesProvider by inject()
    private val appUpdateViewModel: AppUpdateViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mdmProvider = MdmProvider(this)

        if (BuildConfig.FLAVOR == MainApp.MDM_FLAVOR) {
            with(mdmProvider) {
                cacheStringRestriction(CONFIGURATION_SERVER_URL, R.string.server_url_configuration_feedback_ok)
                cacheBooleanRestriction(CONFIGURATION_SERVER_URL_INPUT_VISIBILITY, R.string.server_url_input_visibility_configuration_feedback_ok)
                cacheIntegerRestriction(CONFIGURATION_LOCK_DELAY_TIME, R.string.lock_delay_configuration_feedback_ok)
                cacheBooleanRestriction(CONFIGURATION_ALLOW_SCREENSHOTS, R.string.allow_screenshots_configuration_feedback_ok)
                cacheStringRestriction(CONFIGURATION_OAUTH2_OPEN_ID_SCOPE, R.string.oauth2_open_id_scope_configuration_feedback_ok)
                cacheStringRestriction(CONFIGURATION_OAUTH2_OPEN_ID_PROMPT, R.string.oauth2_open_id_prompt_configuration_feedback_ok)
                cacheBooleanRestriction(CONFIGURATION_DEVICE_PROTECTION, R.string.device_protection_configuration_feedback_ok)
                cacheBooleanRestriction(CONFIGURATION_REDACT_AUTH_HEADER_LOGS, R.string.redact_auth_header_logs_configuration_feedback_ok)
                cacheBooleanRestriction(CONFIGURATION_SEND_LOGIN_HINT_AND_USER, R.string.send_login_hint_and_user_configuration_feedback_ok)
            }
        }
        sharedPreferences.putBoolean(PREFERENCE_CLEAR_DATA_ALREADY_TRIGGERED, true)

        checkLockDelayEnforced(mdmProvider)

        observeAppUpdateState()

        appUpdateViewModel.checkForUpdate()
    }

    private fun observeAppUpdateState() {
        collectLatestLifecycleFlow(appUpdateViewModel.updateState) { state ->
            when (state) {
                is AppUpdateState.Loading -> {
                    // Optionally show loading indicator
                }

                is AppUpdateState.UpdateAvailable -> {
                    showUpdateDialog(state)
                }

                is AppUpdateState.NoUpdateAvailable -> {
                    navigateToNextScreen()
                }

                is AppUpdateState.Error -> {
                    navigateToNextScreen()
                }
            }
        }
    }

    private fun showUpdateDialog(state: AppUpdateState.UpdateAvailable) {
        val dialog = AppUpdateDialogFragment.newInstance(
            latestVersion = state.updateInfo.latestVersionName,
            releaseNotes = state.updateInfo.releaseDate,
            updateUrl = state.updateInfo.updateUrl,
            listener = this
        )
        dialog.show(supportFragmentManager, AppUpdateDialogFragment.TAG)
    }

    private fun navigateToNextScreen() {
        val nextScreenIntent = Intent(
            this,
            if (isAccountAvailable()) FileDisplayActivity::class.java else LoginActivity::class.java
        )
        startActivity(nextScreenIntent)
        finish()
    }

    override fun onUpdateClicked() {
        navigateToNextScreen()
    }

    override fun onSkipClicked() {
        navigateToNextScreen()
    }

    private fun checkLockDelayEnforced(mdmProvider: MdmProvider) {

        val lockDelayEnforced = mdmProvider.getBrandingInteger(CONFIGURATION_LOCK_DELAY_TIME, R.integer.lock_delay_enforced)
        val lockTimeout = LockTimeout.parseFromInteger(lockDelayEnforced)

        if (lockTimeout != LockTimeout.DISABLED) {
            OCSharedPreferencesProvider(this@SplashActivity).putString(PREFERENCE_LOCK_TIMEOUT, lockTimeout.name)
        }
    }

    private fun isAccountAvailable(): Boolean {
        val accountManager = AccountManager.get(this)
        val accounts = accountManager.getAccountsByType(MainApp.accountType)
        return accounts.size > 0
    }
}
