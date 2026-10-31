package com.streamvault.data.sync.foreground

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.work.WorkManager
import com.streamvault.data.sync.providerAutomaticIndexWorkUniqueName
import com.streamvault.data.sync.providerEpgWorkUniqueName
import com.streamvault.data.sync.providerWorkUniqueName
import com.streamvault.data.sync.stalkerIndexWorkUniqueName

/** Handles the notification Cancel action for one provider. */
class SyncCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL_SYNC) return
        val providerId = intent.getLongExtra(EXTRA_PROVIDER_ID, -1L)
        if (providerId <= 0L) return
        cancelProviderSync(
            providerId = providerId,
            cancelUniqueWork = { name -> WorkManager.getInstance(context).cancelUniqueWork(name) },
            pause = SyncUserPauseStore(context)
        )
    }

    companion object {
        const val ACTION_CANCEL_SYNC = "com.streamvault.data.sync.CANCEL_PROVIDER_SYNC"
        const val EXTRA_PROVIDER_ID = "provider_id"

        fun intent(context: Context, providerId: Long): Intent =
            Intent(context, SyncCancelReceiver::class.java)
                .setAction(ACTION_CANCEL_SYNC)
                .setPackage(context.packageName)
                .putExtra(EXTRA_PROVIDER_ID, providerId)
    }
}

/** One-time provider work stopped by Cancel; periodic schedules are left in place. */
internal fun providerOneTimeSyncWorkNames(providerId: Long): List<String> = listOf(
    providerWorkUniqueName(providerId),
    providerAutomaticIndexWorkUniqueName(providerId),
    stalkerIndexWorkUniqueName(providerId),
    providerEpgWorkUniqueName(providerId),
    "provider-automatic-check-$providerId"
)

internal fun cancelProviderSync(
    providerId: Long,
    cancelUniqueWork: (String) -> Unit,
    pause: SyncUserPauseStore?,
    nowMillis: Long = System.currentTimeMillis()
) {
    pause?.pause(providerId, nowMillis)
    providerOneTimeSyncWorkNames(providerId).forEach { name ->
        runCatching { cancelUniqueWork(name) }
            .onFailure { Log.w("SyncCancelReceiver", "Could not cancel $name", it) }
    }
    SyncCancellationRegistry.cancel(providerId)
}

/** Keeps automatic checks from restarting a provider right after the user cancelled it. */
class SyncUserPauseStore internal constructor(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    )

    fun pause(providerId: Long, nowMillis: Long = System.currentTimeMillis()) {
        preferences.edit { putLong(key(providerId), nowMillis + PAUSE_MILLIS) }
    }

    fun isPaused(providerId: Long, nowMillis: Long = System.currentTimeMillis()): Boolean =
        isAutomaticSyncPaused(preferences.getLong(key(providerId), 0L), nowMillis)

    fun clear(providerId: Long) {
        preferences.edit { remove(key(providerId)) }
    }

    private fun key(providerId: Long) = "paused_until_$providerId"

    companion object {
        private const val PREFERENCES = "sync_user_pause"
        const val PAUSE_MILLIS = 60 * 60 * 1000L
    }
}

internal fun isAutomaticSyncPaused(pausedUntilMillis: Long, nowMillis: Long): Boolean =
    pausedUntilMillis > nowMillis && pausedUntilMillis - nowMillis <= SyncUserPauseStore.PAUSE_MILLIS

/** Automatic checks skip a provider the user cancelled within the pause window. */
internal fun isAutomaticSyncPausedByUser(context: Context, providerId: Long): Boolean =
    runCatching { SyncUserPauseStore(context).isPaused(providerId) }.getOrDefault(false)
