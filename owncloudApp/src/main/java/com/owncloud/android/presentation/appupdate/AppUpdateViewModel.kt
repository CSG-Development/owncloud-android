package com.owncloud.android.presentation.appupdate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.BuildConfig
import com.owncloud.android.data.appupdate.datasources.AppUpdateRepository
import com.owncloud.android.domain.appupdate.AppUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for checking app updates
 */
class AppUpdateViewModel(
    private val appUpdateRepository: AppUpdateRepository,
) : ViewModel() {

    private val _updateState = MutableSharedFlow<AppUpdateState>()
    val updateState: Flow<AppUpdateState> = _updateState

    /**
     * Check for available app updates
     */
    fun checkForUpdate() {
        viewModelScope.launch {
            _updateState.emit(AppUpdateState.Loading)
            try {
                val appUpdate = appUpdateRepository.checkForUpdate()

                if (isUpdateAvailable(appUpdate)) {
                    _updateState.emit(AppUpdateState.UpdateAvailable(appUpdate))
                } else {
                    _updateState.emit(AppUpdateState.NoUpdateAvailable)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check for app update")
                _updateState.emit(AppUpdateState.Error(e))
            }
        }
    }

    private fun isUpdateAvailable(appUpdate: AppUpdate): Boolean {
        return appUpdate.latestVersionCode > BuildConfig.VERSION_CODE
    }
}

/**
 * Sealed class representing the state of app update check
 */
sealed class AppUpdateState {
    data object Loading : AppUpdateState()
    data object NoUpdateAvailable : AppUpdateState()
    data class UpdateAvailable(val updateInfo: AppUpdate) : AppUpdateState()
    data class Error(val exception: Exception) : AppUpdateState()
}
