package com.owncloud.android.domain.device

import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.domain.device.model.DevicePathType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MergeLocalAndRemoteDevicesTest {

    @Test
    fun `returns null when both inputs are null`() {
        assertNull(mergeLocalAndRemoteDevices(local = null, remote = null))
    }

    @Test
    fun `returns local as-is when remote is null`() {
        val local = Device(
            id = "",
            name = "Local",
            availablePaths = mapOf(DevicePathType.LOCAL to "https://l/files"),
            certificateCommonName = "cn",
        )
        assertEquals(local, mergeLocalAndRemoteDevices(local, null))
    }

    @Test
    fun `returns remote as-is when local is null`() {
        val remote = Device(
            id = "seagate-1",
            name = "Remote",
            availablePaths = mapOf(
                DevicePathType.PUBLIC to "https://p/files",
                DevicePathType.REMOTE to "https://r/files",
            ),
            certificateCommonName = "cn",
        )
        assertEquals(remote, mergeLocalAndRemoteDevices(null, remote))
    }

    @Test
    fun `merges by certificateCommonName preferring LOCAL from mDNS and metadata from remote`() {
        val local = Device(
            id = "",
            name = "Local",
            availablePaths = mapOf(DevicePathType.LOCAL to "https://192.168.1.50/files"),
            certificateCommonName = "matching-cn",
        )
        val remote = Device(
            id = "seagate-42",
            name = "Friendly Name",
            availablePaths = mapOf(
                DevicePathType.PUBLIC to "https://public/files",
                DevicePathType.REMOTE to "https://relay/files",
            ),
            certificateCommonName = "matching-cn",
        )

        val merged = mergeLocalAndRemoteDevices(local, remote)!!

        assertEquals("seagate-42", merged.id)
        assertEquals("Friendly Name", merged.name)
        assertEquals("matching-cn", merged.certificateCommonName)
        assertEquals(
            mapOf(
                DevicePathType.LOCAL to "https://192.168.1.50/files",
                DevicePathType.PUBLIC to "https://public/files",
                DevicePathType.REMOTE to "https://relay/files",
            ),
            merged.availablePaths,
        )
    }

    @Test
    fun `local LOCAL path overrides remote LOCAL path when both exist`() {
        val local = Device(
            id = "",
            name = "L",
            availablePaths = mapOf(DevicePathType.LOCAL to "https://mdns/files"),
            certificateCommonName = "cn",
        )
        val remote = Device(
            id = "id",
            name = "R",
            availablePaths = mapOf(
                DevicePathType.LOCAL to "https://stale-local/files",
                DevicePathType.PUBLIC to "https://p/files",
            ),
            certificateCommonName = "cn",
        )

        val merged = mergeLocalAndRemoteDevices(local, remote)!!
        assertEquals("https://mdns/files", merged.availablePaths[DevicePathType.LOCAL])
    }

    @Test
    fun `differing certificate names still merges and keeps remote as source of truth`() {
        // Reference behaviour: still keep LOCAL from mDNS just in case it happens to be
        // reachable. Remote wins for everything else (including the resulting CN).
        val local = Device(
            id = "",
            name = "L",
            availablePaths = mapOf(DevicePathType.LOCAL to "https://l/files"),
            certificateCommonName = "local-cn",
        )
        val remote = Device(
            id = "id",
            name = "R",
            availablePaths = mapOf(DevicePathType.PUBLIC to "https://p/files"),
            certificateCommonName = "remote-cn",
        )
        val merged = mergeLocalAndRemoteDevices(local, remote)!!
        assertEquals("remote-cn", merged.certificateCommonName)
        assertEquals("https://l/files", merged.availablePaths[DevicePathType.LOCAL])
        assertEquals("https://p/files", merged.availablePaths[DevicePathType.PUBLIC])
    }

    @Test
    fun `falls back to local certificate name when remote name is empty`() {
        val local = Device(
            id = "",
            name = "L",
            availablePaths = mapOf(DevicePathType.LOCAL to "https://l/files"),
            certificateCommonName = "local-cn",
        )
        val remote = Device(
            id = "id",
            name = "R",
            availablePaths = mapOf(DevicePathType.PUBLIC to "https://p/files"),
            certificateCommonName = "",
        )
        val merged = mergeLocalAndRemoteDevices(local, remote)!!
        assertEquals("local-cn", merged.certificateCommonName)
    }
}
