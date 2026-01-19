package com.owncloud.android.domain.device.usecases

import com.owncloud.android.domain.device.CurrentDeviceRepository

class GetSavedDeviceCertificateUseCase(
    private val currentDeviceRepository: CurrentDeviceRepository
) {

    operator fun invoke(): String? = currentDeviceRepository.getSavedCertificateCommonName()
}

