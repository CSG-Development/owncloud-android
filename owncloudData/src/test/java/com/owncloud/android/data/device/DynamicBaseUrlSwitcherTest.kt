package com.owncloud.android.data.device

import android.accounts.Account
import com.owncloud.android.data.connectivity.Connectivity
import com.owncloud.android.data.connectivity.NetworkStateObserver
import com.owncloud.android.domain.device.usecases.UpdateBaseUrlUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@ExperimentalCoroutinesApi
class DynamicBaseUrlSwitcherTest {

    private val networkStateObserver: NetworkStateObserver = mockk()
    private val updateBaseUrlUseCase: UpdateBaseUrlUseCase = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val switcher = DynamicBaseUrlSwitcher(
        networkStateObserver = networkStateObserver,
        coroutineScope = testScope,
        updateBaseUrlUseCase = updateBaseUrlUseCase
    )

    private val testAccount: Account = mockk(relaxed = true)

    @Test
    fun `startDynamicUrlSwitching triggers update when network available with fromBackground true`() = runTest(testDispatcher) {
        val networkFlow = MutableStateFlow(Connectivity(setOf(Connectivity.ConnectionType.WIFI)))
        every { networkStateObserver.observeNetworkState() } returns networkFlow

        switcher.startDynamicUrlSwitching(testAccount, fromBackground)
        advanceUntilIdle()

        assertTrue(switcher.isActive())
        // First call should have fromBackground = true
        coVerify { updateBaseUrlUseCase.execute(fromBackground = true) }
    }

    @Test
    fun `startDynamicUrlSwitching does not trigger update when no network`() = runTest(testDispatcher) {
        val networkFlow = MutableStateFlow(Connectivity.unavailable())
        every { networkStateObserver.observeNetworkState() } returns networkFlow

        switcher.startDynamicUrlSwitching(testAccount, fromBackground)
        advanceUntilIdle()

        assertTrue(switcher.isActive())
        coVerify(exactly = 0) { updateBaseUrlUseCase.execute(any()) }
    }

    @Test
    fun `stopDynamicUrlSwitching stops observing and clears state`() = runTest(testDispatcher) {
        val networkFlow = MutableStateFlow(Connectivity(setOf(Connectivity.ConnectionType.WIFI)))
        every { networkStateObserver.observeNetworkState() } returns networkFlow

        switcher.startDynamicUrlSwitching(testAccount, fromBackground)
        advanceUntilIdle()
        
        assertTrue(switcher.isActive())
        
        switcher.stopDynamicUrlSwitching()
        
        assertFalse(switcher.isActive())
    }

    @Test
    fun `startDynamicUrlSwitching cancels previous observation`() = runTest(testDispatcher) {
        val networkFlow = MutableStateFlow(Connectivity(setOf(Connectivity.ConnectionType.WIFI)))
        every { networkStateObserver.observeNetworkState() } returns networkFlow

        val account1 = Account("user1@example.com", "owncloud")
        val account2 = Account("user2@example.com", "owncloud")

        switcher.startDynamicUrlSwitching(account1, fromBackground)
        advanceUntilIdle()
        
        assertTrue(switcher.isActive())

        // Start with a different account - should cancel previous
        switcher.startDynamicUrlSwitching(account2, fromBackground)
        advanceUntilIdle()
        
        assertTrue(switcher.isActive())
    }

    @Test
    fun `isActive returns false when not started`() {
        assertFalse(switcher.isActive())
    }

    @Test
    fun `dispose cleans up resources`() = runTest(testDispatcher) {
        val networkFlow = MutableStateFlow(Connectivity(setOf(Connectivity.ConnectionType.WIFI)))
        every { networkStateObserver.observeNetworkState() } returns networkFlow

        switcher.startDynamicUrlSwitching(testAccount, fromBackground)
        advanceUntilIdle()
        
        assertTrue(switcher.isActive())
        
        switcher.dispose()
        
        assertFalse(switcher.isActive())
    }

    @Test
    fun `network state change triggers update with fromBackground true on first network availability`() = runTest(testDispatcher) {
        val networkFlow = MutableStateFlow(Connectivity.unavailable())
        every { networkStateObserver.observeNetworkState() } returns networkFlow

        switcher.startDynamicUrlSwitching(testAccount, fromBackground)
        advanceUntilIdle()

        // Initially no network - no update
        coVerify(exactly = 0) { updateBaseUrlUseCase.execute(any()) }

        // Network becomes available - this IS the initial foreground check (first network availability),
        // so fromBackground should be true
        networkFlow.value = Connectivity(setOf(Connectivity.ConnectionType.WIFI))
        advanceUntilIdle()

        // Should trigger update with fromBackground = true (first network availability after start)
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(fromBackground = true) }
    }

    @Test
    fun `subsequent network state changes trigger update with fromBackground false`() = runTest(testDispatcher) {
        val networkFlow = MutableStateFlow(Connectivity(setOf(Connectivity.ConnectionType.WIFI)))
        every { networkStateObserver.observeNetworkState() } returns networkFlow

        switcher.startDynamicUrlSwitching(testAccount, fromBackground)
        advanceUntilIdle()

        // First call has fromBackground = true
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(fromBackground = true) }

        // Network changes (e.g., switches to mobile)
        networkFlow.value = Connectivity(setOf(Connectivity.ConnectionType.MOBILE))
        advanceUntilIdle()

        // Second call should have fromBackground = false
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(fromBackground = false) }
    }
}
