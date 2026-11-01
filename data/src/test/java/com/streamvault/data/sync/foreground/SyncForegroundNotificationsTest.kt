package com.streamvault.data.sync.foreground

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncForegroundNotificationsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val notifications = SyncForegroundNotifications(context)
    private val movies = SyncNotificationProgress(7L, "Strong", SyncNotificationStage.MOVIES, 120_000, 400_000)

    @Test
    fun `foreground info declares dataSync and keeps the provider slot`() {
        val id = SyncForegroundNotifications.notificationId(7L)

        val info = notifications.foregroundInfo(id, movies)

        assertThat(info.notificationId).isEqualTo(id)
        assertThat(info.foregroundServiceType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        assertThat(info.notification.channelId).isEqualTo(SyncForegroundNotifications.CHANNEL_ID)
        assertThat(SyncForegroundNotifications.notificationId(7L)).isNotEqualTo(SyncForegroundNotifications.notificationId(8L))
    }

    @Test
    fun `notification shows provider, section counts and a cancel action`() {
        val notification = notifications.build(movies)

        assertThat(notification.extras.getString(NotificationCompat.EXTRA_TITLE)).isEqualTo("Strong")
        assertThat(notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
            .isEqualTo("Movies: 120,000 / 400,000")
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(notification.actions).hasLength(1)
        assertThat(notification.actions[0].title.toString()).isEqualTo("Cancel")
        val broadcast = shadowOf(notification.actions[0].actionIntent).savedIntent
        assertThat(broadcast.action).isEqualTo(SyncCancelReceiver.ACTION_CANCEL_SYNC)
        assertThat(broadcast.getLongExtra(SyncCancelReceiver.EXTRA_PROVIDER_ID, -1L)).isEqualTo(7L)
    }

    @Test
    fun `work without a known provider has no cancel action`() {
        val notification = notifications.build(SyncNotificationProgress(-1L, null, SyncNotificationStage.PREPARING))

        assertThat(notification.actions).isNull()
        assertThat(notification.extras.getString(NotificationCompat.EXTRA_TITLE)).isEqualTo("Updating catalog")
    }

    @Test
    fun `channel is created with low importance`() {
        notifications.ensureChannel()

        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(SyncForegroundNotifications.CHANNEL_ID)
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    @Test
    @Config(sdk = [34], qualifiers = "de")
    fun `notification text is localized`() {
        val notification = SyncForegroundNotifications(RuntimeEnvironment.getApplication()).build(movies)

        assertThat(notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
            .isEqualTo("Filme: 120.000 / 400.000")
        assertThat(notification.actions[0].title.toString()).isEqualTo("Abbrechen")
    }
}
