package com.streamvault.app.sync

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streamvault.app.R
import com.streamvault.app.device.isTelevisionDevice
import com.streamvault.data.sync.foreground.SyncForegroundActivity

/** Asks once for notifications and battery exemption when the first long sync runs (phones only). */
@Composable
fun SyncBackgroundGuidanceHost() {
    val context = LocalContext.current
    val store = remember(context) { SyncBackgroundGuidanceStore(context) }
    val syncActive by SyncForegroundActivity.active.collectAsStateWithLifecycle()
    var revision by remember { mutableIntStateOf(0) }
    val step = remember(syncActive, revision) {
        nextSyncGuidanceStep(
            SyncGuidanceInputs(
                syncActive = syncActive,
                isTelevision = context.isTelevisionDevice(),
                sdkInt = Build.VERSION.SDK_INT,
                notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED,
                notificationsRequested = store.notificationsRequested,
                ignoringBatteryOptimizations = context.isIgnoringBatteryOptimizations(),
                batteryPromptDismissed = store.batteryPromptDismissed
            )
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        revision++
    }
    LaunchedEffect(step) {
        if (step == SyncGuidanceStep.REQUEST_NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            store.markNotificationsRequested()
            runCatching { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                .onFailure { revision++ }
        }
    }
    if (step != SyncGuidanceStep.BATTERY_PROMPT) return
    val dismiss: () -> Unit = {
        store.dismissBatteryPrompt()
        revision++
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.sync_battery_prompt_title)) },
        text = { Text(stringResource(R.string.sync_battery_prompt_message)) },
        confirmButton = {
            TextButton(onClick = {
                dismiss()
                context.openBatteryOptimizationSettings()
            }) { Text(stringResource(R.string.sync_battery_prompt_open)) }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.sync_battery_prompt_dismiss)) }
        }
    )
}
