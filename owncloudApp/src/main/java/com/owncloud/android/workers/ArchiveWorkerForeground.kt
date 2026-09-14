package com.owncloud.android.workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.owncloud.android.utils.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import com.owncloud.android.utils.NotificationUtils
import java.util.UUID

object ArchiveWorkerForeground {

    fun notificationIdFor(workerId: UUID): Int = workerId.hashCode()

    fun createForegroundInfo(
        context: Context,
        notificationId: Int,
        title: String,
    ): ForegroundInfo {
        val notification = NotificationUtils.newNotificationBuilder(context, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return ForegroundInfo(notificationId, notification, serviceType)
    }
}
