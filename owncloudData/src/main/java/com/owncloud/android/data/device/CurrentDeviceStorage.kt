package com.owncloud.android.data.device

import com.owncloud.android.data.providers.SharedPreferencesProvider
import com.owncloud.android.domain.device.model.DevicePathType

/**
 * Storage for current device access paths.
 * Stores LOCAL, PUBLIC, and REMOTE base URLs for the device along with a timestamp used as
 * the cache TTL marker (per reference: 1 hour) and the Remote-Access [seagateDeviceID]
 * required for direct path lookup.
 *
 * @author Alexey Pushkarev
 */
class CurrentDeviceStorage(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * Save device base URL by type.
     *
     * Note: this method does NOT touch the cache timestamp; callers that perform a full
     * paths refresh should explicitly call [savePathsTimestamp] after writing all paths.
     */
    fun saveDeviceBaseUrl(pathType: String, baseUrl: String) {
        sharedPreferencesProvider.putString(buildKey(pathType), baseUrl)
    }

    fun saveCertificateCommonName(commonName: String) {
        sharedPreferencesProvider.putString(KEY_CERTIFICATE_COMMON_NAME, commonName)
    }

    fun getCertificateCommonName(): String? {
        return sharedPreferencesProvider.getString(KEY_CERTIFICATE_COMMON_NAME, null)
    }

    /**
     * Get device base URL by type.
     */
    fun getDeviceBaseUrl(pathType: String): String? {
        return sharedPreferencesProvider.getString(buildKey(pathType), null)
    }

    /**
     * Persist the Remote-Access [seagateDeviceID]. Required for the network-change fast-path
     * which can request fresh paths for a known device id without re-running mDNS discovery.
     */
    fun saveSeagateDeviceId(seagateDeviceId: String) {
        sharedPreferencesProvider.putString(KEY_SEAGATE_DEVICE_ID, seagateDeviceId)
    }

    fun getSeagateDeviceId(): String? {
        return sharedPreferencesProvider.getString(KEY_SEAGATE_DEVICE_ID, null)
    }

    /**
     * Stamp the cached paths with the current epoch milliseconds provided by [timeProvider].
     */
    fun savePathsTimestamp() {
        savePathsTimestamp(timeProvider())
    }

    /**
     * Stamp the cached paths with the given [timestampMs].
     *
     * Kept as an explicit overload (rather than a single default-arg function) because
     * mockk's proxy subclassing does not invoke the real constructor, leaving
     * [timeProvider] null in mocks. A plain overload sidesteps Kotlin's
     * synthetic `$default` static method which would evaluate the default value before
     * dispatching to the proxy.
     */
    fun savePathsTimestamp(timestampMs: Long) {
        sharedPreferencesProvider.putLong(KEY_DEVICE_PATHS_TIMESTAMP_MS, timestampMs)
    }

    fun getPathsTimestamp(): Long {
        return sharedPreferencesProvider.getLong(KEY_DEVICE_PATHS_TIMESTAMP_MS, 0L)
    }

    /**
     * Returns true when the cached paths are older than [ttlMs] (default 1 hour, matching
     * the reference algorithm) or when no timestamp is recorded at all.
     */
    fun arePathsExpired(ttlMs: Long = DEFAULT_PATHS_TTL_MS): Boolean {
        val ts = getPathsTimestamp()
        if (ts == 0L) return true
        return (timeProvider() - ts) >= ttlMs
    }

    /**
     * Replace the stored path map atomically: clear all existing entries, write the new
     * ones and stamp the timestamp.
     */
    fun replacePaths(paths: Map<DevicePathType, String>) {
        DevicePathType.entries.forEach { type ->
            sharedPreferencesProvider.removePreference(buildKey(type.name))
        }
        paths.forEach { (type, url) ->
            sharedPreferencesProvider.putString(buildKey(type.name), url)
        }
        savePathsTimestamp()
    }

    /**
     * Clear all stored device paths and the cache timestamp. Does NOT clear the
     * [seagateDeviceID] nor the certificate common name; both should only be cleared on
     * logout via [clearDeviceIdentity].
     */
    fun clearDevicePaths() {
        DevicePathType.entries.forEach { type ->
            sharedPreferencesProvider.removePreference(buildKey(type.name))
        }
        sharedPreferencesProvider.removePreference(KEY_DEVICE_PATHS_TIMESTAMP_MS)
    }

    /**
     * Clear all device-related state including the [seagateDeviceID] and the certificate
     * common name. Intended to be called on logout / account removal.
     */
    fun clearDeviceIdentity() {
        clearDevicePaths()
        sharedPreferencesProvider.removePreference(KEY_SEAGATE_DEVICE_ID)
        sharedPreferencesProvider.removePreference(KEY_CERTIFICATE_COMMON_NAME)
    }

    private fun buildKey(pathType: String): String = KEY_PREFIX + pathType

    companion object {
        private const val KEY_PREFIX = "KEY_DEVICE_PATH"
        private const val KEY_CERTIFICATE_COMMON_NAME = "KEY_CERTIFICATE_COMMON_NAME"
        private const val KEY_SEAGATE_DEVICE_ID = "KEY_SEAGATE_DEVICE_ID"
        private const val KEY_DEVICE_PATHS_TIMESTAMP_MS = "KEY_DEVICE_PATHS_TIMESTAMP_MS"

        const val DEFAULT_PATHS_TTL_MS: Long = 60L * 60L * 1000L
    }
}
