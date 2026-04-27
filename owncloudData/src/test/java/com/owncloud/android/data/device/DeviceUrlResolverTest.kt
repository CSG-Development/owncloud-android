package com.owncloud.android.data.device

import com.owncloud.android.data.mdnsdiscovery.HCDeviceVerificationClient
import com.owncloud.android.domain.device.model.DevicePathType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@ExperimentalCoroutinesApi
class DeviceUrlResolverTest {

    private val deviceVerificationClient: HCDeviceVerificationClient = mockk()

    private val resolver = HCDeviceUrlResolver(deviceVerificationClient)

    @Test
    fun `testPriorityPaths returns LOCAL url when LOCAL succeeds`() = runTest {
        coEvery { deviceVerificationClient.verifyDevice("https://local", isLocal = true) } returns true
        // PUBLIC is probed in parallel; stub its reply so mockk does not throw even
        // though the resolver will cancel it as soon as LOCAL succeeds.
        coEvery { deviceVerificationClient.verifyDevice("https://public", isLocal = false) } returns true

        val result = resolver.testPriorityPaths(
            mapOf(
                DevicePathType.LOCAL to "https://local/files",
                DevicePathType.PUBLIC to "https://public/files",
            ),
            wifiAvailable = true,
        )

        assertEquals("https://local/files", result)
    }

    @Test
    fun `testPriorityPaths returns PUBLIC url when LOCAL fails`() = runTest {
        coEvery { deviceVerificationClient.verifyDevice("https://local", isLocal = true) } returns false
        coEvery { deviceVerificationClient.verifyDevice("https://public", isLocal = false) } returns true

        val result = resolver.testPriorityPaths(
            mapOf(
                DevicePathType.LOCAL to "https://local/files",
                DevicePathType.PUBLIC to "https://public/files",
            ),
            wifiAvailable = true,
        )

        assertEquals("https://public/files", result)
        coVerify { deviceVerificationClient.verifyDevice("https://local", isLocal = true) }
        coVerify { deviceVerificationClient.verifyDevice("https://public", isLocal = false) }
    }

    @Test
    fun `testPriorityPaths skips LOCAL when wifi unavailable`() = runTest {
        coEvery { deviceVerificationClient.verifyDevice("https://public", isLocal = false) } returns true

        val result = resolver.testPriorityPaths(
            mapOf(
                DevicePathType.LOCAL to "https://local/files",
                DevicePathType.PUBLIC to "https://public/files",
            ),
            wifiAvailable = false,
        )

        assertEquals("https://public/files", result)
        coVerify(exactly = 0) { deviceVerificationClient.verifyDevice("https://local", any()) }
    }

    @Test
    fun `testPriorityPaths LOCAL wins even when PUBLIC responds first`() = runTest {
        coEvery { deviceVerificationClient.verifyDevice("https://local", isLocal = true) } coAnswers {
            // local is slower
            delay(50)
            true
        }
        coEvery { deviceVerificationClient.verifyDevice("https://public", isLocal = false) } coAnswers {
            // public responds quickly first
            true
        }

        val result = resolver.testPriorityPaths(
            mapOf(
                DevicePathType.LOCAL to "https://local/files",
                DevicePathType.PUBLIC to "https://public/files",
            ),
            wifiAvailable = true,
        )
        // Even though public was instant, local is preferred when it ultimately succeeds.
        assertEquals("https://local/files", result)
    }

    @Test
    fun `testPriorityPaths returns null when all priority paths fail`() = runTest {
        coEvery { deviceVerificationClient.verifyDevice("https://local", isLocal = true) } returns false
        coEvery { deviceVerificationClient.verifyDevice("https://public", isLocal = false) } returns false

        val result = resolver.testPriorityPaths(
            mapOf(
                DevicePathType.LOCAL to "https://local/files",
                DevicePathType.PUBLIC to "https://public/files",
            ),
            wifiAvailable = true,
        )
        assertNull(result)
    }

    @Test
    fun `testPriorityPaths ignores REMOTE entry`() = runTest {
        // No LOCAL/PUBLIC, only REMOTE provided. testPriorityPaths must not probe it.
        val result = resolver.testPriorityPaths(
            mapOf(DevicePathType.REMOTE to "https://relay/files"),
            wifiAvailable = true,
        )
        assertNull(result)
        coVerify(exactly = 0) { deviceVerificationClient.verifyDevice(any(), any()) }
    }

    @Test
    fun `testSinglePath returns the url when reachable`() = runTest {
        coEvery { deviceVerificationClient.verifyDevice("https://relay", isLocal = false) } returns true

        val result = resolver.testSinglePath("https://relay/files", isLocal = false)
        assertEquals("https://relay/files", result)
    }

    @Test
    fun `testSinglePath returns null when unreachable`() = runTest {
        coEvery { deviceVerificationClient.verifyDevice("https://relay", isLocal = false) } returns false

        val result = resolver.testSinglePath("https://relay/files", isLocal = false)
        assertNull(result)
    }

    // Legacy sequential resolver — kept for the login flow.

    @Test
    fun `resolveAvailableBaseUrl returns LOCAL url sequentially`() = runTest {
        coEvery { deviceVerificationClient.verifyDevice("https://local", isLocal = true) } returns true
        val result = resolver.resolveAvailableBaseUrl(
            mapOf(DevicePathType.LOCAL to "https://local/files"),
        )
        assertEquals("https://local/files", result)
    }

    @Test
    fun `resolveAvailableBaseUrl falls back through priorities`() = runTest {
        coEvery { deviceVerificationClient.verifyDevice("https://local", isLocal = true) } returns false
        coEvery { deviceVerificationClient.verifyDevice("https://public", isLocal = false) } returns false
        coEvery { deviceVerificationClient.verifyDevice("https://remote", isLocal = false) } returns true
        val result = resolver.resolveAvailableBaseUrl(
            mapOf(
                DevicePathType.LOCAL to "https://local/files",
                DevicePathType.PUBLIC to "https://public/files",
                DevicePathType.REMOTE to "https://remote/files",
            ),
        )
        assertEquals("https://remote/files", result)
    }
}
