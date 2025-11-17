package com.owncloud.android.data.device

import android.accounts.Account
import android.accounts.AccountManager
import com.owncloud.android.lib.common.accounts.AccountUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@ExperimentalCoroutinesApi
class DynamicBaseUrlSwitcherTest {

    private val accountManager: AccountManager = mockk(relaxed = true)
    private val baseUrlChooser: BaseUrlChooser = mockk()
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val switcher = DynamicBaseUrlSwitcher(
        accountManager = accountManager,
        baseUrlChooser = baseUrlChooser,
        coroutineScope = testScope
    )

    private val testAccount = Account("test@example.com", "owncloud")

    @Test
    fun `startDynamicUrlSwitching starts observing base URL changes`() = runTest(testDispatcher) {
        val baseUrlFlow = flowOf("https://192.168.1.100/files")
        every { baseUrlChooser.observeAvailableBaseUrl() } returns baseUrlFlow
        every { accountManager.getUserData(testAccount, AccountUtils.Constants.KEY_OC_BASE_URL) } returns null

        switcher.startDynamicUrlSwitching(testAccount)
        advanceUntilIdle()

        assertTrue(switcher.isActive())
        assertEquals(testAccount, switcher.getCurrentAccount())
        verify { accountManager.setUserData(testAccount, AccountUtils.Constants.KEY_OC_BASE_URL, "https://192.168.1.100/files") }
    }

    @Test
    fun `startDynamicUrlSwitching updates base URL when it changes`() = runTest(testDispatcher) {
        val baseUrlFlow = flowOf(
            "https://192.168.1.100/files",
            "https://public.example.com/files"
        )
        every { baseUrlChooser.observeAvailableBaseUrl() } returns baseUrlFlow
        every { 
            accountManager.getUserData(testAccount, AccountUtils.Constants.KEY_OC_BASE_URL) 
        } returns "https://192.168.1.100/files" andThen "https://public.example.com/files"

        switcher.startDynamicUrlSwitching(testAccount)
        advanceUntilIdle()

        verify(exactly = 2) { 
            accountManager.setUserData(
                testAccount, 
                AccountUtils.Constants.KEY_OC_BASE_URL, 
                any()
            ) 
        }
    }

    @Test
    fun `startDynamicUrlSwitching does not update when URL is unchanged`() = runTest(testDispatcher) {
        val baseUrl = "https://192.168.1.100/files"
        val baseUrlFlow = flowOf(baseUrl, baseUrl, baseUrl)
        every { baseUrlChooser.observeAvailableBaseUrl() } returns baseUrlFlow
        every { accountManager.getUserData(testAccount, AccountUtils.Constants.KEY_OC_BASE_URL) } returns baseUrl

        switcher.startDynamicUrlSwitching(testAccount)
        advanceUntilIdle()

        // Should not update since URL hasn't changed
        verify(exactly = 0) { 
            accountManager.setUserData(any(), any(), any()) 
        }
    }

    @Test
    fun `startDynamicUrlSwitching does not update when new URL is null`() = runTest(testDispatcher) {
        val baseUrlFlow = flowOf("https://192.168.1.100/files", null)
        every { baseUrlChooser.observeAvailableBaseUrl() } returns baseUrlFlow
        every { 
            accountManager.getUserData(testAccount, AccountUtils.Constants.KEY_OC_BASE_URL) 
        } returns "https://192.168.1.100/files"

        switcher.startDynamicUrlSwitching(testAccount)
        advanceUntilIdle()

        // Should only update once (for the first non-null value)
        verify(exactly = 1) { 
            accountManager.setUserData(any(), any(), any()) 
        }
    }

    @Test
    fun `stopDynamicUrlSwitching stops observing and clears state`() = runTest(testDispatcher) {
        val baseUrlFlow = flowOf("https://192.168.1.100/files")
        every { baseUrlChooser.observeAvailableBaseUrl() } returns baseUrlFlow
        every { accountManager.getUserData(testAccount, AccountUtils.Constants.KEY_OC_BASE_URL) } returns null

        switcher.startDynamicUrlSwitching(testAccount)
        advanceUntilIdle()
        
        assertTrue(switcher.isActive())
        
        switcher.stopDynamicUrlSwitching()
        
        assertFalse(switcher.isActive())
        assertNull(switcher.getCurrentAccount())
    }

    @Test
    fun `startDynamicUrlSwitching cancels previous observation`() = runTest(testDispatcher) {
        val baseUrlFlow1 = flowOf("https://192.168.1.100/files")
        val baseUrlFlow2 = flowOf("https://public.example.com/files")
        
        every { baseUrlChooser.observeAvailableBaseUrl() } returns baseUrlFlow1 andThen baseUrlFlow2
        every { accountManager.getUserData(any(), any()) } returns null

        val account1 = Account("user1@example.com", "owncloud")
        val account2 = Account("user2@example.com", "owncloud")

        switcher.startDynamicUrlSwitching(account1)
        advanceUntilIdle()
        
        assertEquals(account1, switcher.getCurrentAccount())
        
        // Start with a different account - should cancel previous
        switcher.startDynamicUrlSwitching(account2)
        advanceUntilIdle()
        
        assertEquals(account2, switcher.getCurrentAccount())
    }

    @Test
    fun `isActive returns false when not started`() {
        assertFalse(switcher.isActive())
    }

    @Test
    fun `getCurrentAccount returns null when not started`() {
        assertNull(switcher.getCurrentAccount())
    }

    @Test
    fun `dispose cleans up resources`() = runTest(testDispatcher) {
        val baseUrlFlow = flowOf("https://192.168.1.100/files")
        every { baseUrlChooser.observeAvailableBaseUrl() } returns baseUrlFlow
        every { accountManager.getUserData(testAccount, AccountUtils.Constants.KEY_OC_BASE_URL) } returns null

        switcher.startDynamicUrlSwitching(testAccount)
        advanceUntilIdle()
        
        assertTrue(switcher.isActive())
        
        switcher.dispose()
        
        assertFalse(switcher.isActive())
        assertNull(switcher.getCurrentAccount())
    }

    @Test
    fun `handleBaseUrlChange handles AccountManager exceptions gracefully`() = runTest(testDispatcher) {
        val baseUrlFlow = flowOf("https://192.168.1.100/files")
        every { baseUrlChooser.observeAvailableBaseUrl() } returns baseUrlFlow
        every { accountManager.getUserData(testAccount, AccountUtils.Constants.KEY_OC_BASE_URL) } returns null
        every { 
            accountManager.setUserData(testAccount, AccountUtils.Constants.KEY_OC_BASE_URL, any()) 
        } throws SecurityException("Permission denied")

        switcher.startDynamicUrlSwitching(testAccount)
        advanceUntilIdle()

        // Should not crash, just log the error
        assertTrue(switcher.isActive())
    }
}

