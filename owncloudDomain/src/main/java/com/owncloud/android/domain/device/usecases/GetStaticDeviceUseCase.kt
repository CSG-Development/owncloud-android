package com.owncloud.android.domain.device.usecases

import com.owncloud.android.domain.device.StaticDeviceRepository
import com.owncloud.android.domain.device.model.Device

class GetStaticDeviceUseCase(
    private val staticDeviceRepository: StaticDeviceRepository
) {

    fun execute(): Device? {
        return staticDeviceRepository.getStaticDevice()
    }
}