package com.owncloud.android.data.mdnsdiscovery.repository

import com.owncloud.android.data.mdnsdiscovery.HCDeviceVerificationClient
import com.owncloud.android.data.mdnsdiscovery.datasources.LocalMdnsDiscoveryDataSource
import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.domain.mdnsdiscovery.MdnsDiscoveryRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import kotlin.time.Duration

/**
 * Implementation of MdnsDiscoveryRepository
 *
 * This repository discovers devices via mDNS and verifies each discovered device
 * by calling the proper endpoint. Only verified devices are emitted.
 */
class HCMdnsDiscoveryRepository(
    private val localMdnsDiscoveryDataSource: LocalMdnsDiscoveryDataSource,
    private val deviceVerificationClient: HCDeviceVerificationClient,
) : MdnsDiscoveryRepository {

    override fun discoverAndVerifyDevices(
        duration: Duration
    ): Flow<Device> {
        Timber.d("Starting mDNS discovery with verification - duration: $duration")

        return localMdnsDiscoveryDataSource.discoverDevices(
            duration = duration
        ).mapNotNull { baseUrl ->
            verifyDeviceBaseUrl(baseUrl)
        }
    }

    override suspend fun oneShotDiscoverAndVerifyDevices(duration: Duration): List<Device> {
        val result = mutableListOf<Device>()
        try {
            withTimeout(duration) {
                discoverAndVerifyDevices(
                    duration = duration
                )
                    .collect { result.add(it) }
            }
        } catch (_: TimeoutCancellationException) {
            Timber.d("Local devices found: ${result.size}")
        }
        return result.toList()
    }

    private suspend fun verifyDeviceBaseUrl(baseUrl: String): Device? {
        // Verify each discovered device independently
        Timber.d("Device discovered via mDNS: $baseUrl - verifying...")

        val isVerified = deviceVerificationClient.verifyDevice(baseUrl)

        return if (isVerified) {
            Timber.d("Device verified: $baseUrl")

            // Get certificate common name
            val deviceInfo = deviceVerificationClient.getDeviceInfo(baseUrl)
            val certificateCommonName = deviceInfo?.certificateCommonName
            Timber.d("Device certificate common name: $certificateCommonName")
            val deviceUrl = "$baseUrl/files"

            // Emit device

            Device(
                id = deviceUrl,
                name = certificateCommonName?.takeIf { it.isNotEmpty() } ?: deviceUrl,
                availablePaths = mapOf(
                    DevicePathType.LOCAL to deviceUrl
                ),
                certificateCommonName = certificateCommonName.orEmpty()
            )
        } else {
            Timber.d("Device verification failed, skipping: $baseUrl")
            null
        }
    }
}

