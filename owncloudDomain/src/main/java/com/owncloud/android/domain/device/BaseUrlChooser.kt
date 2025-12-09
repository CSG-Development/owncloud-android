package com.owncloud.android.domain.device

/**
 * Interface for choosing the best available base URL.
 * 
 * Priority order: LOCAL > PUBLIC > REMOTE
 */
interface BaseUrlChooser {

    /**
     * Chooses the best available base URL from stored device paths.
     * 
     * Verifies device availability before returning.
     *
     * @return The best available base URL, or null if none are available
     */
    suspend fun chooseBestAvailableBaseUrl(): String?
}

