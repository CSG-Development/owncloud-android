package com.owncloud.android.data.mdnsdiscovery.datasources

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Interface for mDNS device discovery
 */
interface LocalMdnsDiscoveryDataSource {
    /**
     * Discovers devices using mDNS/Bonjour service discovery
     *
     * @param duration How long to run discovery
     * @return Flow of device URLs as they are discovered
     */
    fun discoverDevices(
        duration: Duration,
    ): Flow<String>
}