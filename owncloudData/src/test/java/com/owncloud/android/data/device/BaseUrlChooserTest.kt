package com.owncloud.android.data.device

import app.cash.turbine.test
import com.owncloud.android.data.connectivity.Connectivity
import com.owncloud.android.data.connectivity.NetworkStateObserver
import com.owncloud.android.data.mdnsdiscovery.HCDeviceVerificationClient
import com.owncloud.android.domain.device.model.DevicePathType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@ExperimentalCoroutinesApi
class BaseUrlChooserTest {

    private val networkStateObserver: NetworkStateObserver = mockk()
    private val currentDeviceStorage: CurrentDeviceStorage = mockk()
    private val deviceVerificationClient: HCDeviceVerificationClient = mockk()

    private val chooser = BaseUrlChooser(
        networkStateObserver,
        currentDeviceStorage,
        deviceVerificationClient
    )

    @Test
    fun `observeAvailableBaseUrl returns null when no network available`() = runTest {
        val connectivityFlow = flowOf(Connectivity.unavailable())
        every { networkStateObserver.observeNetworkState() } returns connectivityFlow

        chooser.observeAvailableBaseUrl().test {
            val result = awaitItem()
            assertNull(result)
            awaitComplete()
        }
    }

    @Test
    fun `observeAvailableBaseUrl returns LOCAL url when available`() = runTest {
        val connectivityFlow = flowOf(
            Connectivity(setOf(Connectivity.ConnectionType.WIFI))
        )
        every { networkStateObserver.observeNetworkState() } returns connectivityFlow
        
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.LOCAL.name) } returns "https://192.168.1.100/files"
        coEvery { deviceVerificationClient.verifyDevice("https://192.168.1.100") } returns true

        chooser.observeAvailableBaseUrl().test {
            val result = awaitItem()
            assertEquals("https://192.168.1.100/files", result)
            awaitComplete()
        }
        
        coVerify { deviceVerificationClient.verifyDevice("https://192.168.1.100") }
    }

    @Test
    fun `observeAvailableBaseUrl returns PUBLIC url when LOCAL is not available`() = runTest {
        val connectivityFlow = flowOf(
            Connectivity(setOf(Connectivity.ConnectionType.WIFI))
        )
        every { networkStateObserver.observeNetworkState() } returns connectivityFlow
        
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.LOCAL.name) } returns "https://192.168.1.100/files"
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.PUBLIC.name) } returns "https://public.example.com/files"
        coEvery { deviceVerificationClient.verifyDevice("https://192.168.1.100") } returns false
        coEvery { deviceVerificationClient.verifyDevice("https://public.example.com") } returns true

        chooser.observeAvailableBaseUrl().test {
            val result = awaitItem()
            assertEquals("https://public.example.com/files", result)
            awaitComplete()
        }
        
        coVerify(exactly = 1) { deviceVerificationClient.verifyDevice("https://192.168.1.100") }
        coVerify(exactly = 1) { deviceVerificationClient.verifyDevice("https://public.example.com") }
    }

    @Test
    fun `observeAvailableBaseUrl returns REMOTE url when LOCAL and PUBLIC are not available`() = runTest {
        val connectivityFlow = flowOf(
            Connectivity(setOf(Connectivity.ConnectionType.CELLULAR))
        )
        every { networkStateObserver.observeNetworkState() } returns connectivityFlow
        
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.LOCAL.name) } returns "https://192.168.1.100/files"
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.PUBLIC.name) } returns "https://public.example.com/files"
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.REMOTE.name) } returns "https://remote.example.com/files"
        coEvery { deviceVerificationClient.verifyDevice("https://192.168.1.100") } returns false
        coEvery { deviceVerificationClient.verifyDevice("https://public.example.com") } returns false
        coEvery { deviceVerificationClient.verifyDevice("https://remote.example.com") } returns true

        chooser.observeAvailableBaseUrl().test {
            val result = awaitItem()
            assertEquals("https://remote.example.com/files", result)
            awaitComplete()
        }
        
        coVerify(exactly = 1) { deviceVerificationClient.verifyDevice("https://192.168.1.100") }
        coVerify(exactly = 1) { deviceVerificationClient.verifyDevice("https://public.example.com") }
        coVerify(exactly = 1) { deviceVerificationClient.verifyDevice("https://remote.example.com") }
    }

    @Test
    fun `observeAvailableBaseUrl returns null when all URLs are unavailable`() = runTest {
        val connectivityFlow = flowOf(
            Connectivity(setOf(Connectivity.ConnectionType.WIFI))
        )
        every { networkStateObserver.observeNetworkState() } returns connectivityFlow
        
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.LOCAL.name) } returns "https://192.168.1.100/files"
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.PUBLIC.name) } returns "https://public.example.com/files"
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.REMOTE.name) } returns "https://remote.example.com/files"
        coEvery { deviceVerificationClient.verifyDevice(any()) } returns false

        chooser.observeAvailableBaseUrl().test {
            val result = awaitItem()
            assertNull(result)
            awaitComplete()
        }
    }

    @Test
    fun `observeAvailableBaseUrl returns null when no URLs are stored`() = runTest {
        val connectivityFlow = flowOf(
            Connectivity(setOf(Connectivity.ConnectionType.WIFI))
        )
        every { networkStateObserver.observeNetworkState() } returns connectivityFlow
        
        every { currentDeviceStorage.getDeviceBaseUrl(any()) } returns null

        chooser.observeAvailableBaseUrl().test {
            val result = awaitItem()
            assertNull(result)
            awaitComplete()
        }
        
        coVerify(exactly = 0) { deviceVerificationClient.verifyDevice(any()) }
    }

    @Test
    fun `observeAvailableBaseUrl skips missing URLs in priority order`() = runTest {
        val connectivityFlow = flowOf(
            Connectivity(setOf(Connectivity.ConnectionType.WIFI))
        )
        every { networkStateObserver.observeNetworkState() } returns connectivityFlow
        
        // LOCAL is missing
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.LOCAL.name) } returns null
        // PUBLIC is available
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.PUBLIC.name) } returns "https://public.example.com/files"
        coEvery { deviceVerificationClient.verifyDevice("https://public.example.com") } returns true

        chooser.observeAvailableBaseUrl().test {
            val result = awaitItem()
            assertEquals("https://public.example.com/files", result)
            awaitComplete()
        }
        
        // LOCAL was not verified since it's missing
        coVerify(exactly = 0) { deviceVerificationClient.verifyDevice("https://192.168.1.100") }
        coVerify(exactly = 1) { deviceVerificationClient.verifyDevice("https://public.example.com") }
    }

    @Test
    fun `observeAvailableBaseUrl emits distinct values on network changes`() = runTest {
        val connectivityFlow = flowOf(
            Connectivity(setOf(Connectivity.ConnectionType.WIFI)),
            Connectivity(setOf(Connectivity.ConnectionType.CELLULAR)),
            Connectivity(setOf(Connectivity.ConnectionType.WIFI)) // Same result expected
        )
        every { networkStateObserver.observeNetworkState() } returns connectivityFlow
        
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.LOCAL.name) } returns "https://192.168.1.100/files"
        coEvery { deviceVerificationClient.verifyDevice("https://192.168.1.100") } returns true

        chooser.observeAvailableBaseUrl().test {
            // Should emit only once due to distinctUntilChanged
            val result = awaitItem()
            assertEquals("https://192.168.1.100/files", result)
            awaitComplete()
        }
    }
}

