package com.owncloud.android.data.connectivity

import com.owncloud.android.data.connectivity.Connectivity.ConnectionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityTest {

    @Test
    fun `hasAnyNetwork is false for unavailable`() {
        assertFalse(Connectivity.unavailable().hasAnyNetwork())
        assertFalse(Connectivity(setOf(ConnectionType.NONE)).hasAnyNetwork())
    }

    @Test
    fun `hasAnyNetwork is true when any non-NONE connection`() {
        assertTrue(Connectivity(setOf(ConnectionType.WIFI)).hasAnyNetwork())
        assertTrue(Connectivity(setOf(ConnectionType.CELLULAR)).hasAnyNetwork())
        assertTrue(Connectivity(setOf(ConnectionType.VPN)).hasAnyNetwork())
        assertTrue(Connectivity(setOf(ConnectionType.ETHERNET)).hasAnyNetwork())
    }

    @Test
    fun `isLanLikely is true for WIFI ETHERNET VPN`() {
        assertTrue(Connectivity(setOf(ConnectionType.WIFI)).isLanLikely())
        assertTrue(Connectivity(setOf(ConnectionType.ETHERNET)).isLanLikely())
        assertTrue(Connectivity(setOf(ConnectionType.VPN)).isLanLikely())
        // mixed transports also count
        assertTrue(Connectivity(setOf(ConnectionType.CELLULAR, ConnectionType.WIFI)).isLanLikely())
    }

    @Test
    fun `isLanLikely is false for cellular-only or unknown`() {
        assertFalse(Connectivity(setOf(ConnectionType.CELLULAR)).isLanLikely())
        assertFalse(Connectivity(setOf(ConnectionType.NONE)).isLanLikely())
        assertFalse(Connectivity.unavailable().isLanLikely())
    }

    @Test
    fun `isWifiStateUnknown is true only for empty or only-NONE state`() {
        assertTrue(Connectivity(emptySet()).isWifiStateUnknown())
        assertTrue(Connectivity(setOf(ConnectionType.NONE)).isWifiStateUnknown())
        assertTrue(Connectivity.unavailable().isWifiStateUnknown())
        assertFalse(Connectivity(setOf(ConnectionType.WIFI)).isWifiStateUnknown())
        assertFalse(Connectivity(setOf(ConnectionType.CELLULAR)).isWifiStateUnknown())
    }
}
