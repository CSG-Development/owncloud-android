package com.owncloud.android.domain.device.usecases

import android.accounts.Account

/**
 * Use case for managing dynamic base URL switching for user accounts.
 * 
 * Delegates to the underlying repository/implementation to handle:
 * - Starting URL observation when user logs in
 * - Stopping URL observation when user logs out
 * - Checking if switching is currently active
 */
interface ManageDynamicUrlSwitchingUseCase {
    
    /**
     * Start dynamic base URL switching for the specified account.
     * 
     * This will begin observing network state changes and automatically
     * update the account's base URL to the best available option.
     * 
     * @param account The account to manage
     */
    fun startDynamicUrlSwitching(account: Account)
    
    /**
     * Stop dynamic base URL switching.
     * 
     * This should be called when:
     * - User logs out
     * - Account is removed
     * - App is shutting down
     */
    fun stopDynamicUrlSwitching()
    
    /**
     * Check if dynamic URL switching is currently active.
     * 
     * @return true if actively managing an account, false otherwise
     */
    fun isActive(): Boolean
    
    /**
     * Get the currently managed account, if any.
     * 
     * @return The account being managed, or null if not active
     */
    fun getCurrentAccount(): Account?
}

