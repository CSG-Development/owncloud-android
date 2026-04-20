package com.owncloud.android.data.device

import com.owncloud.android.data.providers.SharedPreferencesProvider
import com.owncloud.android.domain.device.model.DevicePathType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentDeviceStorageTest {

    private val prefs: SharedPreferencesProvider = mockk(relaxed = true)
    private var fakeNow: Long = 0L
    private val storage = CurrentDeviceStorage(prefs) { fakeNow }

    @Test
    fun `arePathsExpired returns true when no timestamp`() {
        every { prefs.getLong("KEY_DEVICE_PATHS_TIMESTAMP_MS", 0L) } returns 0L
        assertTrue(storage.arePathsExpired())
    }

    @Test
    fun `arePathsExpired returns false when within TTL`() {
        every { prefs.getLong("KEY_DEVICE_PATHS_TIMESTAMP_MS", 0L) } returns 1_000L
        fakeNow = 1_000L + (CurrentDeviceStorage.DEFAULT_PATHS_TTL_MS / 2)
        assertFalse(storage.arePathsExpired())
    }

    @Test
    fun `arePathsExpired returns true when older than TTL`() {
        every { prefs.getLong("KEY_DEVICE_PATHS_TIMESTAMP_MS", 0L) } returns 1_000L
        fakeNow = 1_000L + CurrentDeviceStorage.DEFAULT_PATHS_TTL_MS + 1
        assertTrue(storage.arePathsExpired())
    }

    @Test
    fun `arePathsExpired honors custom ttl`() {
        every { prefs.getLong("KEY_DEVICE_PATHS_TIMESTAMP_MS", 0L) } returns 0L
        fakeNow = 100L
        // ts==0 always means expired regardless of ttl, but with a stamp:
        every { prefs.getLong("KEY_DEVICE_PATHS_TIMESTAMP_MS", 0L) } returns 50L
        assertFalse(storage.arePathsExpired(ttlMs = 200L))
        assertTrue(storage.arePathsExpired(ttlMs = 10L))
    }

    @Test
    fun `replacePaths clears all entries and writes new ones with timestamp`() {
        fakeNow = 12_345L
        val paths = mapOf(
            DevicePathType.LOCAL to "https://l/files",
            DevicePathType.PUBLIC to "https://p/files",
        )
        storage.replacePaths(paths)

        DevicePathType.entries.forEach {
            verify { prefs.removePreference("KEY_DEVICE_PATH${it.name}") }
        }
        verify { prefs.putString("KEY_DEVICE_PATHLOCAL", "https://l/files") }
        verify { prefs.putString("KEY_DEVICE_PATHPUBLIC", "https://p/files") }
        verify { prefs.putLong("KEY_DEVICE_PATHS_TIMESTAMP_MS", 12_345L) }
    }

    @Test
    fun `savePathsTimestamp uses provided value when explicit`() {
        storage.savePathsTimestamp(99L)
        verify { prefs.putLong("KEY_DEVICE_PATHS_TIMESTAMP_MS", 99L) }
    }

    @Test
    fun `seagate device id is stored and read`() {
        every { prefs.getString("KEY_SEAGATE_DEVICE_ID", null) } returns "seagate-1"
        storage.saveSeagateDeviceId("seagate-1")
        verify { prefs.putString("KEY_SEAGATE_DEVICE_ID", "seagate-1") }
        assertEquals("seagate-1", storage.getSeagateDeviceId())
    }

    @Test
    fun `clearDevicePaths removes paths and timestamp but keeps identity`() {
        storage.clearDevicePaths()
        DevicePathType.entries.forEach {
            verify { prefs.removePreference("KEY_DEVICE_PATH${it.name}") }
        }
        verify { prefs.removePreference("KEY_DEVICE_PATHS_TIMESTAMP_MS") }
        verify(exactly = 0) { prefs.removePreference("KEY_SEAGATE_DEVICE_ID") }
        verify(exactly = 0) { prefs.removePreference("KEY_CERTIFICATE_COMMON_NAME") }
    }

    @Test
    fun `clearDeviceIdentity removes paths timestamp seagate id and certificate`() {
        storage.clearDeviceIdentity()
        verify { prefs.removePreference("KEY_DEVICE_PATHS_TIMESTAMP_MS") }
        verify { prefs.removePreference("KEY_SEAGATE_DEVICE_ID") }
        verify { prefs.removePreference("KEY_CERTIFICATE_COMMON_NAME") }
    }

    @Test
    fun `getDeviceBaseUrl returns null when not set`() {
        every { prefs.getString("KEY_DEVICE_PATHLOCAL", null) } returns null
        assertNull(storage.getDeviceBaseUrl(DevicePathType.LOCAL.name))
    }

    @Test
    fun `saveDeviceBaseUrl uses correct key prefix and does not touch timestamp`() {
        val keySlot = slot<String>()
        val valueSlot = slot<String>()
        every { prefs.putString(capture(keySlot), capture(valueSlot)) } returns Unit

        storage.saveDeviceBaseUrl(DevicePathType.PUBLIC.name, "https://x/files")

        assertEquals("KEY_DEVICE_PATHPUBLIC", keySlot.captured)
        assertEquals("https://x/files", valueSlot.captured)
        verify(exactly = 0) { prefs.putLong("KEY_DEVICE_PATHS_TIMESTAMP_MS", any()) }
    }
}
