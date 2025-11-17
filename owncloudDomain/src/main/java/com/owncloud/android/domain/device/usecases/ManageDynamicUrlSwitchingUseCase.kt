package com.owncloud.android.domain.device.usecases

/**
 * Use case for managing dynamic base URL switching for user accounts.
 * 
 * Delegates to the underlying repository/implementation to handle:
 * - Starting URL observation when user logs in
 * - Stopping URL observation when user logs out
 * - Checking if switching is currently active
 */
interface ManageDynamicUrlSwitchingUseCase {

    fun initDynamicUrlSwitching()

    /**
     * Start dynamic base URL switching for the specified account.
     * 
     * This will begin observing network state changes and automatically
     * update the account's base URL to the best available option.
     * 
     * @param account The account to manage
     */
    fun startDynamicUrlSwitching()
    
    /**
     * Stop dynamic base URL switching.
     * 
     * This should be called when:
     * - User logs out
     * - Account is removed
     * - App is shutting down
     */
    fun stopDynamicUrlSwitching()
}

