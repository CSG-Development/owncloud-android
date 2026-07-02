package com.owncloud.android.presentation.trash

import java.util.concurrent.TimeUnit

object TrashDateUtils {

    private const val DEFAULT_RETENTION_DAYS = 30

    fun daysLeft(deletedTimestamp: Long?, retentionDays: Int = DEFAULT_RETENTION_DAYS): Int? {
        if (deletedTimestamp == null || deletedTimestamp <= 0) {
            return null
        }
        val deletedAtMillis = if (deletedTimestamp > 1_000_000_000_000L) {
            deletedTimestamp
        } else {
            deletedTimestamp * 1000
        }
        val daysSinceDeletion = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - deletedAtMillis)
        return maxOf(0, retentionDays - daysSinceDeletion.toInt())
    }
}
