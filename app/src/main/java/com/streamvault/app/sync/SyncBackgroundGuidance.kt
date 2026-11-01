package com.streamvault.app.sync

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.edit

/** Next one-time step shown when a long catalog sync starts. */
enum class SyncGuidanceStep { NONE, REQUEST_NOTIFICATIONS, BATTERY_PROMPT }

data class SyncGuidanceInputs(
    val syncActive: Boolean,
    val isTelevision: Boolean,
    val sdkInt: Int,
    val notificationsGranted: Boolean,
    val notificationsRequested: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val batteryPromptDismissed: Boolean
)

fun nextSyncGuidanceStep(inputs: SyncGuidanceInputs): SyncGuidanceStep = when {
    !inputs.syncActive || inputs.isTelevision -> SyncGuidanceStep.NONE
    inputs.sdkInt >= TIRAMISU && !inputs.notificationsGranted && !inputs.notificationsRequested ->
        SyncGuidanceStep.REQUEST_NOTIFICATIONS
    !inputs.ignoringBatteryOptimizations && !inputs.batteryPromptDismissed -> SyncGuidanceStep.BATTERY_PROMPT
    else -> SyncGuidanceStep.NONE
}

private const val TIRAMISU = 33

/** Remembers that each guidance step was shown so the user is never asked twice. */
class SyncBackgroundGuidanceStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("sync_background_guidance", Context.MODE_PRIVATE)
    )

    val notificationsRequested: Boolean get() = preferences.getBoolean(KEY_NOTIFICATIONS, false)
    val batteryPromptDismissed: Boolean get() = preferences.getBoolean(KEY_BATTERY, false)

    fun markNotificationsRequested() = preferences.edit { putBoolean(KEY_NOTIFICATIONS, true) }

    fun dismissBatteryPrompt() = preferences.edit { putBoolean(KEY_BATTERY, true) }

    private companion object {
        const val KEY_NOTIFICATIONS = "notifications_requested"
        const val KEY_BATTERY = "battery_prompt_dismissed"
    }
}

fun Context.isIgnoringBatteryOptimizations(): Boolean =
    getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) ?: true

/** Direct exemption request first, then the system list, then the app details page. */
fun batteryOptimizationIntents(packageName: String): List<Intent> = listOf(
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
)

fun Context.openBatteryOptimizationSettings(): Boolean = batteryOptimizationIntents(packageName).any { intent ->
    try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
