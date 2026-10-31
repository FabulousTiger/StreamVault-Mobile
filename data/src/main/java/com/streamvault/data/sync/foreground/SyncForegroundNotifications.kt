package com.streamvault.data.sync.foreground

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.streamvault.data.R

/** Builds the low-importance catalog sync notification and its dataSync [ForegroundInfo]. */
internal class SyncForegroundNotifications(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.sync_fg_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.sync_fg_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(progress: SyncNotificationProgress): Notification {
        ensureChannel()
        val locale = context.resources.configuration.locales[0]
        val text = formatSyncNotification(progress, ResourceSyncNotificationStrings(context.resources), locale)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setProgress(text.progressMax, text.progressValue, text.indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply {
                launchIntent()?.let { setContentIntent(it) }
                cancelIntent(progress.providerId)?.let { cancel ->
                    addAction(0, context.getString(R.string.sync_fg_cancel), cancel)
                }
            }
            .build()
    }

    fun foregroundInfo(notificationId: Int, progress: SyncNotificationProgress): ForegroundInfo {
        val notification = build(progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    /** Re-posts the foreground notification; dropped silently when notifications are blocked. */
    fun update(notificationId: Int, progress: SyncNotificationProgress) {
        if (!canPostNotifications()) return
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, build(progress)) }
    }

    fun cancel(notificationId: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun launchIntent(): PendingIntent? {
        val packageManager = context.packageManager
        val launch = packageManager.getLaunchIntentForPackage(context.packageName)
            ?: packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
            ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancelIntent(providerId: Long): PendingIntent? {
        if (providerId <= 0L) return null
        return PendingIntent.getBroadcast(
            context,
            (providerId % Int.MAX_VALUE).toInt(),
            SyncCancelReceiver.intent(context, providerId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "catalog_sync"
        private const val NOTIFICATION_ID_BASE = 0x53560000
        private const val PROVIDER_SLOTS = 100_000L

        /** One slot per provider so a resumed chain reuses its notification; 0 is the shared slot. */
        fun notificationId(providerId: Long): Int =
            NOTIFICATION_ID_BASE + (providerId.coerceAtLeast(0L) % PROVIDER_SLOTS).toInt()
    }
}
