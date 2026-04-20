package com.owncloud.android.data.device

import android.accounts.Account
import com.owncloud.android.data.connectivity.Connectivity
import com.owncloud.android.data.connectivity.NetworkStateObserver
import com.owncloud.android.data.lifecycle.AppLifecycleObserver
import com.owncloud.android.data.lifecycle.AppState
import com.owncloud.android.domain.device.usecases.UpdateBaseUrlUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@ExperimentalCoroutinesApi
class DynamicBaseUrlSwitcherTest {

    private val networkStateObserver: NetworkStateObserver = mockk()
    private val updateBaseUrlUseCase: UpdateBaseUrlUseCase = mockk(relaxed = true)
    private val appLifecycleObserver: AppLifecycleObserver = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private var fakeNow: Long = 0L

    private fun newSwitcher(
        cooldownMs: Long = DynamicBaseUrlSwitcher.COOLDOWN_MS,
        debounceMs: Long = DynamicBaseUrlSwitcher.DEBOUNCE_MS,
    ) = DynamicBaseUrlSwitcher(
        networkStateObserver = networkStateObserver,
        appLifecycleObserver = appLifecycleObserver,
        coroutineScope = testScope,
        updateBaseUrlUseCase = updateBaseUrlUseCase,
        debounceMs = debounceMs,
        cooldownMs = cooldownMs,
        timeProvider = { fakeNow },
    )

    private val testAccount: Account = mockk(relaxed = true)
    private val wifi = Connectivity(setOf(Connectivity.ConnectionType.WIFI))
    private val cellular = Connectivity(setOf(Connectivity.ConnectionType.CELLULAR))

    @Test
    fun `initial start triggers update with fromBackground=true and wifiAvailable=true`() = runTest(testDispatcher) {
        every { networkStateObserver.observeNetworkState() } returns MutableStateFlow(wifi)
        every { appLifecycleObserver.appState } returns MutableStateFlow(AppState.FOREGROUND)
        every { appLifecycleObserver.isInBackground() } returns false

        val switcher = newSwitcher()
        switcher.startDynamicUrlSwitching(testAccount, fromBackground = true)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            updateBaseUrlUseCase.execute(fromBackground = true, wifiAvailable = true)
        }
        assertTrue(switcher.isActive())
    }

    @Test
    fun `first network emission after start is skipped (skip-first)`() = runTest(testDispatcher) {
        val flow = MutableStateFlow(wifi)
        every { networkStateObserver.observeNetworkState() } returns flow
        every { appLifecycleObserver.appState } returns MutableStateFlow(AppState.FOREGROUND)
        every { appLifecycleObserver.isInBackground() } returns false

        val switcher = newSwitcher()
        switcher.startDynamicUrlSwitching(testAccount, fromBackground = true)
        advanceUntilIdle()

        // The initial detection was triggered (not by network event but by start).
        coVerify(exactly = 1) {
            updateBaseUrlUseCase.execute(fromBackground = true, wifiAvailable = true)
        }

        // Now emit another (still the same) value. Distinct + skip-first means nothing else.
        flow.value = wifi
        advanceUntilIdle()
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }
    }

    @Test
    fun `subsequent change is debounced and triggers after debounce window`() = runTest(testDispatcher) {
        val flow = MutableStateFlow(wifi)
        every { networkStateObserver.observeNetworkState() } returns flow
        every { appLifecycleObserver.appState } returns MutableStateFlow(AppState.FOREGROUND)
        every { appLifecycleObserver.isInBackground() } returns false

        val switcher = newSwitcher(cooldownMs = 0L)
        switcher.startDynamicUrlSwitching(testAccount, fromBackground = true)
        advanceUntilIdle()

        // Initial detection ran.
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // Real change: switch to cellular. After skip-first we still need debounce.
        flow.value = cellular
        // before debounce window elapses we should not have triggered another update
        advanceTimeBy(DynamicBaseUrlSwitcher.DEBOUNCE_MS - 100)
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        advanceTimeBy(200)
        advanceUntilIdle()
        coVerify(exactly = 1) {
            updateBaseUrlUseCase.execute(fromBackground = false, wifiAvailable = false)
        }
    }

    @Test
    fun `cooldown blocks rapid re-triggers`() = runTest(testDispatcher) {
        val flow = MutableStateFlow(wifi)
        every { networkStateObserver.observeNetworkState() } returns flow
        every { appLifecycleObserver.appState } returns MutableStateFlow(AppState.FOREGROUND)
        every { appLifecycleObserver.isInBackground() } returns false

        // Start with a non-zero fake time: production uses 0L as the "not initialized"
        // sentinel for `lastDetectionAtMs`, so leaving fakeNow at 0 during the initial
        // detection would incorrectly bypass the cooldown check on the next trigger.
        fakeNow = 100L
        val switcher = newSwitcher(cooldownMs = 30_000L, debounceMs = 0L)
        switcher.startDynamicUrlSwitching(testAccount, fromBackground = true)
        advanceUntilIdle()
        // Initial detection ran and stamped lastDetection = 100.
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // Skip-first then a change at t+1s. Cooldown not elapsed → should NOT trigger.
        fakeNow = 1_100L
        flow.value = cellular
        advanceUntilIdle()
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // Emit another change inside the cooldown.
        fakeNow = 5_100L
        flow.value = wifi
        advanceUntilIdle()
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // After cooldown expires, the next change does trigger.
        fakeNow = 31_100L
        flow.value = cellular
        advanceUntilIdle()
        coVerify(exactly = 2) { updateBaseUrlUseCase.execute(any(), any()) }
    }

    @Test
    fun `change while in background is deferred and processed on FOREGROUND`() = runTest(testDispatcher) {
        val flow = MutableStateFlow(wifi)
        val lifecycle = MutableStateFlow(AppState.FOREGROUND)
        every { networkStateObserver.observeNetworkState() } returns flow
        every { appLifecycleObserver.appState } returns lifecycle
        every { appLifecycleObserver.isInBackground() } returnsMany listOf(true, true, false)

        val switcher = newSwitcher(cooldownMs = 0L, debounceMs = 0L)
        switcher.startDynamicUrlSwitching(testAccount, fromBackground = true)
        advanceUntilIdle()
        // initial detection done

        // Change in background: should be deferred (no extra trigger).
        flow.value = cellular
        advanceUntilIdle()
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // App returns to foreground: pending change is processed.
        lifecycle.value = AppState.BACKGROUND
        advanceUntilIdle()
        lifecycle.value = AppState.FOREGROUND
        advanceUntilIdle()
        coVerify(exactly = 1) {
            updateBaseUrlUseCase.execute(fromBackground = false, wifiAvailable = false)
        }
    }

    @Test
    fun `cellular-only network sets wifiAvailable=false`() = runTest(testDispatcher) {
        val flow = MutableStateFlow(cellular)
        every { networkStateObserver.observeNetworkState() } returns flow
        every { appLifecycleObserver.appState } returns MutableStateFlow(AppState.FOREGROUND)
        every { appLifecycleObserver.isInBackground() } returns false

        val switcher = newSwitcher(cooldownMs = 0L, debounceMs = 0L)
        // Force the initial detection to be triggered with cellular-only (the initial
        // fake connectivity inside triggerInitialDetection is wifi by design, so we use
        // the network-driven path instead).
        switcher.startDynamicUrlSwitching(testAccount, fromBackground = false)
        advanceUntilIdle()
        // initial detection ran with default wifi-like connectivity
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), wifiAvailable = true) }

        // Real network change to a different cellular state (skip-first then process)
        flow.value = Connectivity(setOf(Connectivity.ConnectionType.CELLULAR, Connectivity.ConnectionType.VPN))
        advanceUntilIdle()
        // VPN counts as LAN-likely → wifiAvailable should still be true.
        coVerify(atLeast = 1) { updateBaseUrlUseCase.execute(any(), wifiAvailable = true) }
    }

    @Test
    fun `no network event does not trigger update`() = runTest(testDispatcher) {
        val flow = MutableStateFlow(Connectivity.unavailable())
        every { networkStateObserver.observeNetworkState() } returns flow
        every { appLifecycleObserver.appState } returns MutableStateFlow(AppState.FOREGROUND)
        every { appLifecycleObserver.isInBackground() } returns false

        val switcher = newSwitcher(cooldownMs = 0L, debounceMs = 0L)
        switcher.startDynamicUrlSwitching(testAccount, fromBackground = false)
        advanceUntilIdle()
        // initial detection always runs (Algorithm A entry)
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // First network emission is skipped; nothing else triggered.
        flow.value = Connectivity.unavailable()
        advanceUntilIdle()
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }
    }

    @Test
    fun `stop cancels observation`() = runTest(testDispatcher) {
        every { networkStateObserver.observeNetworkState() } returns MutableStateFlow(wifi)
        every { appLifecycleObserver.appState } returns MutableStateFlow(AppState.FOREGROUND)
        every { appLifecycleObserver.isInBackground() } returns false

        val switcher = newSwitcher()
        switcher.startDynamicUrlSwitching(testAccount, fromBackground = true)
        advanceUntilIdle()
        assertTrue(switcher.isActive())

        switcher.stopDynamicUrlSwitching()
        assertFalse(switcher.isActive())
    }
}
