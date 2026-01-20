package com.owncloud.android.domain.device.usecases

import com.owncloud.android.domain.device.StaticDeviceRepository

class SaveStaticDeviceUseCase(
    private val staticDeviceRepository: StaticDeviceRepository
) {

    /**
     * Save static device URL.
     * @param remoteUrl The remote URL for the static device
     */
    fun execute(remoteUrl: String) {
        staticDeviceRepository.saveStaticDeviceUrl(remoteUrl)
    }
}
