package com.owncloud.android.data.device

import com.owncloud.android.data.providers.SharedPreferencesProvider

/**
 * Storage for developer options.
 * Stores the manually configured remote URL for static device.
 */
class DeveloperOptionsStorage(
    private val sharedPreferencesProvider: SharedPreferencesProvider
) {

    /**
     * Save static device URL.
     * @param url The remote URL for the static device
     */
    fun saveStaticDeviceUrl(url: String) {
        sharedPreferencesProvider.putString(KEY_STATIC_DEVICE_URL, url)
    }

    /**
     * Get static device URL.
     * @return The stored URL or null if not configured
     */
    fun getStaticDeviceUrl(): String? {
        return sharedPreferencesProvider.getString(KEY_STATIC_DEVICE_URL, null)
    }

    /**
     * Clear static device URL.
     */
    fun clearStaticDeviceUrl() {
        sharedPreferencesProvider.removePreference(KEY_STATIC_DEVICE_URL)
    }

    companion object {
        private const val KEY_STATIC_DEVICE_URL = "static_device_url"
    }
}
