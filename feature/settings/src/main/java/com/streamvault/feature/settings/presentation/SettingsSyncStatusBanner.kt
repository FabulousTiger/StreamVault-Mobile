package com.streamvault.feature.settings.presentation

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamvault.core.ui.progress.extractProgressFraction
import com.streamvault.core.ui.theme.OnSurface
import com.streamvault.core.ui.theme.OnSurfaceDim
import com.streamvault.core.ui.theme.Primary
import com.streamvault.core.ui.theme.SurfaceElevated
import com.streamvault.feature.settings.R
import kotlinx.coroutines.delay

/** Cancellable catalog syncs run behind a small banner; other busy work keeps the modal overlay. */
internal fun SettingsUiState.showsSyncBanner(): Boolean = isSyncing && syncCanCancel

internal fun SettingsUiState.showsBlockingSyncOverlay(): Boolean = isSyncing && !syncCanCancel

/** Non-modal sync status: navigation, focus and settings stay usable while the sync runs. */
@Composable
internal fun SettingsSyncStatusBanner(
    uiState: SettingsUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!uiState.showsSyncBanner()) return
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(uiState.syncStartedAt) {
        while (uiState.syncStartedAt > 0L) {
            elapsedSeconds = (System.currentTimeMillis() - uiState.syncStartedAt) / 1000L
            delay(1_000L)
        }
    }
    val fraction = uiState.syncProgress?.let(::extractProgressFraction)
    Column(
        modifier = modifier
            .widthIn(max = 420.dp)
            .background(SurfaceElevated, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = uiState.syncSectionLabel ?: stringResource(R.string.settings_syncing_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                uiState.syncingProviderName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            OutlinedButton(onClick = onCancel) {
                Text(text = stringResource(R.string.settings_sync_stop_refresh), color = Primary)
            }
        }
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, color = Primary, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(color = Primary, modifier = Modifier.fillMaxWidth())
        }
        uiState.syncProgress?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (uiState.syncStartedAt > 0L) {
            Text(
                text = stringResource(R.string.settings_sync_elapsed, DateUtils.formatElapsedTime(elapsedSeconds.coerceAtLeast(0L))),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceDim
            )
        }
    }
}
