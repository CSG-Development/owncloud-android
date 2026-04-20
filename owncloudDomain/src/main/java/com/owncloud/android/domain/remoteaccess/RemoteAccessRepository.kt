package com.owncloud.android.domain.remoteaccess

import com.owncloud.android.domain.device.model.Device
import com.owncloud.android.domain.device.model.DevicePathType

interface RemoteAccessRepository {
    /**
     * Initiate client authentication
     * @param email User's email address
     * @param clientId Unique client identifier
     * @param clientFriendlyName Human-readable client name
     * @return Reference string to be used in token request
     */
    suspend fun initiateAuthentication(
        email: String,
        clientId: String,
        clientFriendlyName: String
    ): String

    /**
     * Obtain a JWT access token
     * @param reference Reference from initiate response
     * @param code Validation code received by email
     */
    suspend fun getToken(
        reference: String,
        code: String,
        userName: String,
        clientId: String,
    )

    /**
     * Check if access token exists in storage
     * @return true if access token exists, false otherwise
     */
    fun hasAccessToken(): Boolean

    /**
     * Get all available devices with their access paths
     * @return a list of available devices
     */
    suspend fun getAvailableDevices(): List<Device>

    suspend fun getCurrentDevice(): Device?

    /**
     * Fetch fresh connection paths for a specific device id. Used by the cache-refresh
     * fast-path: when the cached paths fail and the cache is expired, we fetch fresh paths
     * for the persisted [seagateDeviceId] without re-running the full discovery flow.
     *
     * @return The map of [DevicePathType] to base URL, or `null` when the request fails or
     *  the response contains no paths.
     */
    suspend fun getDevicePathsById(seagateDeviceId: String): Map<DevicePathType, String>?

    /**
     * Clear all stored device paths
     */
    fun clearDevicePaths()
}

