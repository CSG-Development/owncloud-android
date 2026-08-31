package com.owncloud.android.domain.device.usecases

import com.owncloud.android.domain.device.model.DevicePathType

class RefreshRemoteDevicePathsUseCase(
    private val syncCurrentDevicePathsUseCase: SyncCurrentDevicePathsUseCase,
    private val getCurrentDevicePathsUseCase: GetCurrentDevicePathsUseCase,
) {

    suspend fun execute(): Boolean {
        if (!syncCurrentDevicePathsUseCase.execute(skipMdns = true)) {
            return false
        }
        return !getCurrentDevicePathsUseCase()[DevicePathType.REMOTE].isNullOrEmpty()
    }
}
