package com.owncloud.android.domain.mdnsdiscovery.usecases

import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.domain.mdnsdiscovery.MdnsDiscoveryRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Use case for discovering and verifying devices via mDNS
 */
class DiscoverLocalNetworkDevicesUseCase(
    private val mdnsDiscoveryRepository: MdnsDiscoveryRepository
) {
    
    fun execute(params: Params = DEFAULT_MDNS_PARAMS): Flow<Device> =
        mdnsDiscoveryRepository.discoverAndVerifyDevices(
            duration = params.duration
        )

    suspend fun oneShot(params: Params = DEFAULT_MDNS_PARAMS): List<Device> {
        val result = mutableListOf<Device>()
        try {
            withTimeout(params.duration) {
                mdnsDiscoveryRepository.discoverAndVerifyDevices(
                    duration = params.duration
                )
                    .collect { result.add(it) }
            }
        } catch (_: TimeoutCancellationException) {
            Timber.d("Local devices found: ${result.size}")
        }
        return result.toList()
    }

    data class Params(
        val duration: Duration
    )

    companion object {

        // 5 seconds matches the reference algorithm "local discovery window".
        val DEFAULT_MDNS_PARAMS = Params(
            duration = 5.seconds
        )
    }
}
