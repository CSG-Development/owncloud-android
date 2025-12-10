package com.owncloud.android.domain.device

/**
 * Interface for managing account base URL.
 * 
 * This abstraction allows the domain layer to update account base URLs
 * without depending on AccountManager or other
 * Android-specific implementations.
 */
interface AccountBaseUrlManager {

    /**
     * Gets the current base URL for the active account.
     * 
     * @return Current base URL, or null if no account is active
     */
    fun getCurrentBaseUrl(): String?

    /**
     * Updates the base URL for the active account.
     * 
     * This will:
     * - Update the account's stored base URL
     *
     * @param newBaseUrl The new base URL to set
     * @return true if update was successful, false otherwise
     */
    fun updateBaseUrl(newBaseUrl: String): Boolean

    /**
     * Checks if there is an active account.
     * 
     * @return true if an account is available, false otherwise
     */
    fun hasActiveAccount(): Boolean
}

