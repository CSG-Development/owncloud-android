package com.owncloud.android.domain.server.usecases

import app.cash.turbine.test
import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.domain.device.model.DevicePath
import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.domain.mdnsdiscovery.usecases.DiscoverLocalNetworkDevicesUseCase
import com.owncloud.android.domain.remoteaccess.usecases.GetRemoteAvailableDevicesUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@ExperimentalCoroutinesApi
class GetAvailableDevicesUseCaseTest {

    private val mGetRemoteAvailableDevicesUseCase: GetRemoteAvailableDevicesUseCase = mockk()
    private val discoverLocalNetworkDevicesUseCase: DiscoverLocalNetworkDevicesUseCase = mockk()

    private val useCase = GetAvailableDevicesUseCase(
        mGetRemoteAvailableDevicesUseCase,
        discoverLocalNetworkDevicesUseCase
    )

    @Test
    fun `getServersUpdates should return a merged list of remote and local devices`() = runTest {
        // --- Mocks Setup ---
        val device1DevicePath = DevicePath("Device 1", "https://remote1.com", devicePathType = DevicePathType.REMOTE)
        val devices = listOf(
            Device(
                id = "id1",
                availablePaths = mapOf(DevicePathType.REMOTE to device1DevicePath),
                preferredPath = device1DevicePath
            )
        )
        
        val device2DevicePath = DevicePath("Device 2", "https://remote2.com", devicePathType = DevicePathType.PUBLIC)
        val devices2 = listOf(
            Device(
                id = "id1",
                availablePaths = mapOf(DevicePathType.REMOTE to device1DevicePath),
                preferredPath = device1DevicePath
            ),
            Device(
                id = "id2",
                availablePaths = mapOf(DevicePathType.PUBLIC to device2DevicePath),
                preferredPath = device2DevicePath
            )
        )
        coEvery { mGetRemoteAvailableDevicesUseCase.execute() }.returnsMany(devices, devices2)

        val localDevicePath = DevicePath(hostName = "https://local.com", hostUrl = "https://local.com")
        val localDevicesFlow = flowOf(localDevicePath)
        val params = DiscoverLocalNetworkDevicesUseCase.Params("serviceType", "serviceName", 10.seconds)
        coEvery { discoverLocalNetworkDevicesUseCase.execute(any()) } returns localDevicesFlow

        // --- Scope Setup ---
        val job = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + job)

        // --- Test Execution ---
        useCase.getServersUpdates(scope, params).test {
            useCase.refreshRemoteAccessDevices()
            
            // Consume initial, remote, and combined emissions
            awaitItem()
            awaitItem()
            awaitItem()
            
            useCase.refreshRemoteAccessDevices()
            val finalEmission = awaitItem()

            // Should have 3 devices total: Device 1, Device 2, and a new device for local server
            assertEquals(3, finalEmission.size)
            assertEquals("Device 1", finalEmission[0].name)
            assertEquals("Device 2", finalEmission[1].name)
            assertEquals("https://local.com", finalEmission[2].name)

            // Cleanly finish the Turbine test
            cancelAndConsumeRemainingEvents()
        }

        // --- Cleanup ---
        job.cancel()
    }

    @Test
    fun `getServersUpdates should merge local server into existing device by certificate`() = runTest {
        // --- Mocks Setup ---
        val remoteDevicePath1 = DevicePath("Device 1", "https://remote1.com", "cert-001", DevicePathType.REMOTE)
        val remoteDevicePath2 = DevicePath("Device 2", "https://remote2.com", "cert-002", DevicePathType.PUBLIC)
        val devices = listOf(
            Device(
                id = "id1",
                availablePaths = mapOf(DevicePathType.REMOTE to remoteDevicePath1),
                preferredPath = remoteDevicePath1
            ),
            Device(
                id = "id2",
                availablePaths = mapOf(DevicePathType.PUBLIC to remoteDevicePath2),
                preferredPath = remoteDevicePath2
            )
        )
        coEvery { mGetRemoteAvailableDevicesUseCase.execute() }.returns(devices)

        // Local server with the same certificate as Device 1
        val localDevicePath = DevicePath(
            hostName = "https://local.com", 
            hostUrl = "https://local.com", 
            certificateCommonName = "cert-001"
        )
        val localDevicesFlow = flowOf(localDevicePath)
        val params = DiscoverLocalNetworkDevicesUseCase.Params("serviceType", "serviceName", 10.seconds)
        coEvery { discoverLocalNetworkDevicesUseCase.execute(any()) } returns localDevicesFlow

        // --- Scope Setup ---
        val job = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + job)

        // --- Test Execution ---
        useCase.getServersUpdates(scope, params).test {
            useCase.refreshRemoteAccessDevices()
            
            awaitItem()
            awaitItem()
            val finalEmission = awaitItem()

            // Should still have 2 devices, with local server merged into Device 1
            assertEquals(2, finalEmission.size)
            assertEquals("https://local.com", finalEmission[0].name)
            assertEquals(2, finalEmission[0].availablePaths.size) // Now has both REMOTE and LOCAL
            assertEquals(true, finalEmission[0].availablePaths.containsKey(DevicePathType.REMOTE))
            assertEquals(true, finalEmission[0].availablePaths.containsKey(DevicePathType.LOCAL))
            // Local should be preferred because it's verified
            assertEquals(DevicePathType.LOCAL, finalEmission[0].preferredPath.devicePathType)
            
            assertEquals("Device 2", finalEmission[1].name)
            assertEquals(true, finalEmission[1].availablePaths.containsKey(DevicePathType.PUBLIC))
            assertEquals(1, finalEmission[1].availablePaths.size) // Only has PUBLIC

            // Cleanly finish the Turbine test
            cancelAndConsumeRemainingEvents()
        }

        // --- Cleanup ---
        job.cancel()
    }

    @Test
    fun `getServersUpdates should create separate device for local server with different certificate`() = runTest {
        // --- Mocks Setup ---
        val remoteDevicePath1 = DevicePath("Device 1", "https://remote1.com", "cert-001", DevicePathType.REMOTE)
        val remoteDevicePath2 = DevicePath("Device 2", "https://remote2.com", "cert-002", DevicePathType.PUBLIC)
        val devices = listOf(
            Device(
                id = "id1",
                availablePaths = mapOf(DevicePathType.REMOTE to remoteDevicePath1),
                preferredPath = remoteDevicePath1
            ),
            Device(
                id = "id2",
                availablePaths = mapOf(DevicePathType.PUBLIC to remoteDevicePath2),
                preferredPath = remoteDevicePath2
            )
        )
        coEvery { mGetRemoteAvailableDevicesUseCase.execute() }.returns(devices)

        // Local server with different certificate
        val localDevicePath = DevicePath(
            hostName = "https://local.com", 
            hostUrl = "https://local.com", 
            certificateCommonName = "cert-003"
        )
        val localDevicesFlow = flowOf(localDevicePath)
        val params = DiscoverLocalNetworkDevicesUseCase.Params("serviceType", "serviceName", 10.seconds)
        coEvery { discoverLocalNetworkDevicesUseCase.execute(any()) } returns localDevicesFlow

        // --- Scope Setup ---
        val job = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + job)

        // --- Test Execution ---
        useCase.getServersUpdates(scope, params).test {
            useCase.refreshRemoteAccessDevices()

            awaitItem()
            awaitItem()
            val finalEmission = awaitItem()

            // Should have 3 devices: the 2 remote ones plus a new local one
            assertEquals(3, finalEmission.size)
            assertEquals("Device 1", finalEmission[0].name)
            assertEquals("Device 2", finalEmission[1].name)
            assertEquals("https://local.com", finalEmission[2].name)
            assertEquals(DevicePathType.LOCAL, finalEmission[2].preferredPath.devicePathType)

            // Cleanly finish the Turbine test
            cancelAndConsumeRemainingEvents()
        }

        // --- Cleanup ---
        job.cancel()
    }

    @Test
    fun `getServersUpdates should create separate device for local server with empty certificate`() = runTest {
        // --- Mocks Setup ---
        val remoteDevicePath1 = DevicePath("Device 1", "https://remote1.com", "", DevicePathType.REMOTE)
        val remoteDevicePath2 = DevicePath("Device 2", "https://remote2.com", "", DevicePathType.PUBLIC)
        val devices = listOf(
            Device(
                id = "id1",
                availablePaths = mapOf(DevicePathType.REMOTE to remoteDevicePath1),
                preferredPath = remoteDevicePath1
            ),
            Device(
                id = "id2",
                availablePaths = mapOf(DevicePathType.PUBLIC to remoteDevicePath2),
                preferredPath = remoteDevicePath2
            )
        )
        coEvery { mGetRemoteAvailableDevicesUseCase.execute() }.returns(devices)

        // Local server also without certificate common name
        val localDevicePath = DevicePath(
            hostName = "https://local.com", 
            hostUrl = "https://local.com", 
            certificateCommonName = ""
        )
        val localDevicesFlow = flowOf(localDevicePath)
        val params = DiscoverLocalNetworkDevicesUseCase.Params("serviceType", "serviceName", 10.seconds)
        coEvery { discoverLocalNetworkDevicesUseCase.execute(any()) } returns localDevicesFlow

        // --- Scope Setup ---
        val job = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + job)

        // --- Test Execution ---
        useCase.getServersUpdates(scope, params).test {
            useCase.refreshRemoteAccessDevices()
            
            // Consume initial, remote, and combined emissions
            awaitItem()
            awaitItem()
            val finalEmission = awaitItem()

            // Should have all 3 devices since they don't have certificate common names (can't merge)
            assertEquals(3, finalEmission.size)
            assertEquals("Device 1", finalEmission[0].name)
            assertEquals("Device 2", finalEmission[1].name)
            assertEquals("https://local.com", finalEmission[2].name)

            // Cleanly finish the Turbine test
            cancelAndConsumeRemainingEvents()
        }

        // --- Cleanup ---
        job.cancel()
    }
}