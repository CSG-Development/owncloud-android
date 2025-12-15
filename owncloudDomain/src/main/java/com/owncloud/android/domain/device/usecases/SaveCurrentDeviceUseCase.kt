package com.owncloud.android.domain.device.usecases

import com.owncloud.android.domain.device.CurrentDeviceRepository
import com.owncloud.android.domain.device.model.Device

class SaveCurrentDeviceUseCase(
    private val currentDeviceRepository: CurrentDeviceRepository
) {

    operator fun invoke(device: Device) {
        currentDeviceRepository.saveCurrentDevice(device)
    }
}