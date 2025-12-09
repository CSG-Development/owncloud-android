package com.owncloud.android.data.device

import com.owncloud.android.domain.device.model.DevicePathType
import com.owncloud.android.domain.server.usecases.DeviceUrlResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@ExperimentalCoroutinesApi
class HCBaseUrlChooserTest {

    private val currentDeviceStorage: CurrentDeviceStorage = mockk()
    private val deviceUrlResolver: DeviceUrlResolver = mockk()

    private val chooser = HCBaseUrlChooser(
        currentDeviceStorage,
        deviceUrlResolver
    )

    @Test
    fun `chooseBestAvailableBaseUrl returns LOCAL url when available`() = runTest {
        every { currentDeviceStorage.getDeviceBaseUrl(any()) } returns null
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.LOCAL.name) } returns "https://192.168.1.100/files"
        
        coEvery { deviceUrlResolver.resolveAvailableBaseUrl(any()) } returns "https://192.168.1.100/files"

        val result = chooser.chooseBestAvailableBaseUrl()
        assertEquals("https://192.168.1.100/files", result)
        
        coVerify { 
            deviceUrlResolver.resolveAvailableBaseUrl(
                match { paths ->
                    paths.size == 1 && 
                    paths[DevicePathType.LOCAL] == "https://192.168.1.100/files"
                }
            ) 
        }
    }

    @Test
    fun `chooseBestAvailableBaseUrl returns PUBLIC url when LOCAL is not available`() = runTest {
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.LOCAL.name) } returns null
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.PUBLIC.name) } returns "https://public.example.com/files"
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.REMOTE.name) } returns null
        
        coEvery { deviceUrlResolver.resolveAvailableBaseUrl(any()) } returns "https://public.example.com/files"

        val result = chooser.chooseBestAvailableBaseUrl()
        assertEquals("https://public.example.com/files", result)
        
        coVerify { 
            deviceUrlResolver.resolveAvailableBaseUrl(
                match { paths ->
                    paths.size == 1 && 
                    paths[DevicePathType.PUBLIC] == "https://public.example.com/files"
                }
            ) 
        }
    }

    @Test
    fun `chooseBestAvailableBaseUrl returns REMOTE url when LOCAL and PUBLIC are not available`() = runTest {
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.LOCAL.name) } returns null
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.PUBLIC.name) } returns null
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.REMOTE.name) } returns "https://remote.example.com/files"
        
        coEvery { deviceUrlResolver.resolveAvailableBaseUrl(any()) } returns "https://remote.example.com/files"

        val result = chooser.chooseBestAvailableBaseUrl()
        assertEquals("https://remote.example.com/files", result)
        
        coVerify { 
            deviceUrlResolver.resolveAvailableBaseUrl(
                match { paths ->
                    paths.size == 1 && 
                    paths[DevicePathType.REMOTE] == "https://remote.example.com/files"
                }
            ) 
        }
    }

    @Test
    fun `chooseBestAvailableBaseUrl returns null when all URLs are unavailable`() = runTest {
        every { currentDeviceStorage.getDeviceBaseUrl(any()) } returns "https://example.com/files"
        
        coEvery { deviceUrlResolver.resolveAvailableBaseUrl(any()) } returns null

        val result = chooser.chooseBestAvailableBaseUrl()
        assertNull(result)
    }

    @Test
    fun `chooseBestAvailableBaseUrl returns null when no URLs are stored`() = runTest {
        every { currentDeviceStorage.getDeviceBaseUrl(any()) } returns null
        
        coEvery { deviceUrlResolver.resolveAvailableBaseUrl(any()) } returns null

        val result = chooser.chooseBestAvailableBaseUrl()
        assertNull(result)
        
        coVerify { 
            deviceUrlResolver.resolveAvailableBaseUrl(emptyMap())
        }
    }

    @Test
    fun `chooseBestAvailableBaseUrl builds correct priority order`() = runTest {
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.LOCAL.name) } returns "https://192.168.1.100/files"
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.PUBLIC.name) } returns "https://public.example.com/files"
        every { currentDeviceStorage.getDeviceBaseUrl(DevicePathType.REMOTE.name) } returns "https://remote.example.com/files"
        
        coEvery { deviceUrlResolver.resolveAvailableBaseUrl(any()) } returns "https://192.168.1.100/files"

        val result = chooser.chooseBestAvailableBaseUrl()
        assertEquals("https://192.168.1.100/files", result)
        
        coVerify { 
            deviceUrlResolver.resolveAvailableBaseUrl(
                match { paths ->
                    paths.size == 3 && 
                    paths[DevicePathType.LOCAL] == "https://192.168.1.100/files" &&
                    paths[DevicePathType.PUBLIC] == "https://public.example.com/files" &&
                    paths[DevicePathType.REMOTE] == "https://remote.example.com/files"
                }
            ) 
        }
    }
}

