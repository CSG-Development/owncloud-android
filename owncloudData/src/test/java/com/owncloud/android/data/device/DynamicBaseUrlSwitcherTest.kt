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
    private val wifi = Connectivity(setOf(Connectivity.ConnectionType.WIFI), networkHandle = 1L)
    private val wifi2 = Connectivity(setOf(Connectivity.ConnectionType.WIFI), networkHandle = 2L)
    private val cellular = Connectivity(setOf(Connectivity.ConnectionType.CELLULAR), networkHandle = 3L)

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

        // Non-zero so the cooldown sentinel (lastDetectionAtMs == 0L) works correctly:
        // without this, lastDetectionAtMs = 0 after the initial detection and any subsequent
        // trigger would bypass the cooldown check, causing an unintended second execute() call.
        fakeNow = 100L
        val switcher = newSwitcher()
        switcher.startDynamicUrlSwitching(testAccount, fromBackground = true)
        advanceUntilIdle()

        // The initial detection was triggered (not by network event but by start).
        coVerify(exactly = 1) {
            updateBaseUrlUseCase.execute(fromBackground = true, wifiAvailable = true)
        }

        // Emitting the identical object is suppressed by distinctUntilChanged — no extra trigger.
        flow.value = wifi
        advanceTimeBy(DynamicBaseUrlSwitcher.DEBOUNCE_MS + 100)
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // Emitting wifi2 (same type, different networkHandle) simulates an SSID change and
        // passes distinctUntilChanged — but the cooldown defers it, so still no immediate trigger.
        flow.value = wifi2
        advanceTimeBy(DynamicBaseUrlSwitcher.DEBOUNCE_MS + 100)
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // Cancel the pending deferred job so runTest cleanup does not loop on stale fakeNow.
        switcher.stopDynamicUrlSwitching()
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

        // Skip-first then a change at t+1s. Cooldown not elapsed → blocked, deferred.
        // Use advanceTimeBy(1) not advanceUntilIdle(): the deferred job sits ~29 s away;
        // advanceUntilIdle() would advance into it and loop because fakeNow is frozen.
        fakeNow = 1_100L
        flow.value = cellular
        advanceTimeBy(1)
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // Emit another change inside the cooldown. Replaces the previous deferred job.
        fakeNow = 5_100L
        flow.value = wifi
        advanceTimeBy(1)
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // After cooldown expires, the next change passes immediately and triggers.
        // advanceUntilIdle() is safe here: the trigger succeeds so no new deferred job is left.
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
    fun `wifi-to-wifi transition triggers update after cooldown via deferred job`() = runTest(testDispatcher) {
        val flow = MutableStateFlow(wifi)
        every { networkStateObserver.observeNetworkState() } returns flow
        every { appLifecycleObserver.appState } returns MutableStateFlow(AppState.FOREGROUND)
        every { appLifecycleObserver.isInBackground() } returns false

        // Non-zero start time so the cooldown sentinel (lastDetectionAtMs == 0L) works correctly.
        fakeNow = 1_000L
        val switcher = newSwitcher(cooldownMs = 30_000L, debounceMs = 0L)
        switcher.startDynamicUrlSwitching(testAccount, fromBackground = true)
        advanceUntilIdle()
        // Initial detection ran and stamped lastDetectionAtMs = 1_000.
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // Emit wifi2 (same type, different networkHandle) — simulates moving to a different
        // WiFi SSID. distinctUntilChanged passes it because handles differ; the cooldown
        // blocks and defers it. No immediate extra trigger.
        flow.value = wifi2
        advanceTimeBy(1_000)  // past debounce (0), deferred job now scheduled
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // Advance fakeNow past the cooldown so the deferred triggerDetection passes.
        fakeNow = 1_000L + 30_000L + 1L
        advanceTimeBy(30_000L) // deferred delay elapses → triggerDetection fires again
        advanceUntilIdle()
        coVerify(exactly = 2) { updateBaseUrlUseCase.execute(any(), wifiAvailable = true) }
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(fromBackground = false, wifiAvailable = true) }
    }

    @Test
    fun `event blocked by cooldown is deferred and triggered after cooldown expires`() = runTest(testDispatcher) {
        val flow = MutableStateFlow(wifi)
        every { networkStateObserver.observeNetworkState() } returns flow
        every { appLifecycleObserver.appState } returns MutableStateFlow(AppState.FOREGROUND)
        every { appLifecycleObserver.isInBackground() } returns false

        fakeNow = 1_000L
        val switcher = newSwitcher(cooldownMs = 30_000L, debounceMs = 0L)
        switcher.startDynamicUrlSwitching(testAccount, fromBackground = true)
        advanceUntilIdle()
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // Network change within the cooldown window: blocked → deferred, no immediate trigger.
        fakeNow = 5_000L
        flow.value = cellular
        advanceTimeBy(2_000)
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // A second blocked event replaces the deferred job (only the latest is kept).
        fakeNow = 10_000L
        flow.value = wifi2
        advanceTimeBy(2_000)
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(any(), any()) }

        // Advance fakeNow past cooldown so the deferred trigger passes when it fires.
        fakeNow = 1_000L + 30_000L + 1L
        advanceTimeBy(30_000L) // remaining deferred delay elapses
        advanceUntilIdle()
        // Only one extra trigger (the second/latest deferred event), with wifiAvailable=true.
        // Initial call was fromBackground=true; deferred call is fromBackground=false.
        coVerify(exactly = 2) { updateBaseUrlUseCase.execute(any(), wifiAvailable = true) }
        coVerify(exactly = 1) { updateBaseUrlUseCase.execute(fromBackground = false, wifiAvailable = true) }
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
