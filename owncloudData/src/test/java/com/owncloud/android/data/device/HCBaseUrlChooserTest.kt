package com.owncloud.android.data.device

import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.domain.remoteaccess.RemoteAccessRepository
import com.owncloud.android.domain.server.usecases.DeviceUrlResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@ExperimentalCoroutinesApi
class HCBaseUrlChooserTest {

    private val currentDeviceStorage: CurrentDeviceStorage = mockk(relaxed = true)
    private val deviceUrlResolver: DeviceUrlResolver = mockk()
    private val remoteAccessRepository: RemoteAccessRepository = mockk()

    private val chooser = HCBaseUrlChooser(
        currentDeviceStorage,
        deviceUrlResolver,
        remoteAccessRepository,
    )

    private fun stubPaths(local: String? = null, public: String? = null, remote: String? = null) {
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.LOCAL.name) } returns local
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.PUBLIC.name) } returns public
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.REMOTE.name) } returns remote
    }

    @Test
    fun `returns priority url when resolver succeeds on cached paths`() = runTest {
        stubPaths(local = "https://192.168.1.100/files", public = "https://public.example.com/files")
        coEvery { deviceUrlResolver.testPriorityPaths(any(), wifiAvailable = true) } returns "https://192.168.1.100/files"

        val result = chooser.chooseBestAvailableBaseUrl(wifiAvailable = true)

        assertEquals("https://192.168.1.100/files", result)
        coVerify(exactly = 0) { remoteAccessRepository.getDevicePathsById(any()) }
    }

    @Test
    fun `returns null when no cached paths`() = runTest {
        stubPaths()

        val result = chooser.chooseBestAvailableBaseUrl(wifiAvailable = true)

        assertNull(result)
        coVerify(exactly = 0) { deviceUrlResolver.testPriorityPaths(any(), any()) }
    }

    @Test
    fun `falls back to relay when priority fails and no cache refresh available`() = runTest {
        stubPaths(local = "https://192.168.1.100/files", remote = "https://relay.example.com/files")
        coEvery { deviceUrlResolver.testPriorityPaths(any(), any()) } returns null
        every { currentDeviceStorage.arePathsExpired() } returns false
        coEvery { deviceUrlResolver.testSinglePath("https://relay.example.com/files", isLocal = false) } returns "https://relay.example.com/files"

        val result = chooser.chooseBestAvailableBaseUrl(wifiAvailable = true)

        assertEquals("https://relay.example.com/files", result)
        coVerify(exactly = 0) { remoteAccessRepository.getDevicePathsById(any()) }
    }

    @Test
    fun `cache refresh skipped when not expired`() = runTest {
        stubPaths(local = "https://l/files")
        coEvery { deviceUrlResolver.testPriorityPaths(any(), any()) } returns null
        every { currentDeviceStorage.arePathsExpired() } returns false
        every { currentDeviceStorage.getSeagateDeviceId() } returns "id-1"
        every { remoteAccessRepository.hasAccessToken() } returns true

        chooser.chooseBestAvailableBaseUrl(wifiAvailable = true)

        coVerify(exactly = 0) { remoteAccessRepository.getDevicePathsById(any()) }
    }

    @Test
    fun `cache refresh identical only updates timestamp`() = runTest {
        val cached = mapOf(DevicePathType.LOCAL to "https://l/files", DevicePathType.PUBLIC to "https://p/files")
        stubPaths(local = "https://l/files", public = "https://p/files")
        coEvery { deviceUrlResolver.testPriorityPaths(any(), any()) } returns null
        every { currentDeviceStorage.arePathsExpired() } returns true
        every { currentDeviceStorage.getSeagateDeviceId() } returns "id-1"
        every { remoteAccessRepository.hasAccessToken() } returns true
        coEvery { remoteAccessRepository.getDevicePathsById("id-1") } returns cached

        val result = chooser.chooseBestAvailableBaseUrl(wifiAvailable = true)

        // Same paths returned: only timestamp updated, no second resolver attempt, no relay (none configured).
        verify(exactly = 1) { currentDeviceStorage.savePathsTimestamp() }
        coVerify(exactly = 1) { deviceUrlResolver.testPriorityPaths(any(), any()) }
        assertNull(result)
    }

    @Test
    fun `cache refresh differing replaces cache and re-tests`() = runTest {
        stubPaths(local = "https://old-local/files")
        val freshPaths = mapOf(DevicePathType.LOCAL to "https://new-local/files")
        coEvery { deviceUrlResolver.testPriorityPaths(match { it[DevicePathType.LOCAL] == "https://old-local/files" }, any()) } returns null
        coEvery { deviceUrlResolver.testPriorityPaths(match { it[DevicePathType.LOCAL] == "https://new-local/files" }, any()) } returns "https://new-local/files"
        every { currentDeviceStorage.arePathsExpired() } returns true
        every { currentDeviceStorage.getSeagateDeviceId() } returns "id-1"
        every { remoteAccessRepository.hasAccessToken() } returns true
        coEvery { remoteAccessRepository.getDevicePathsById("id-1") } returns freshPaths

        val result = chooser.chooseBestAvailableBaseUrl(wifiAvailable = true)

        assertEquals("https://new-local/files", result)
        verify(exactly = 1) { currentDeviceStorage.replacePaths(freshPaths) }
    }

    @Test
    fun `wifiAvailable=false is forwarded to resolver`() = runTest {
        stubPaths(local = "https://l/files", public = "https://p/files")
        coEvery { deviceUrlResolver.testPriorityPaths(any(), wifiAvailable = false) } returns "https://p/files"

        val result = chooser.chooseBestAvailableBaseUrl(wifiAvailable = false)

        assertEquals("https://p/files", result)
        coVerify { deviceUrlResolver.testPriorityPaths(any(), wifiAvailable = false) }
    }
}
