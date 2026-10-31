package com.streamvault.feature.settings.presentation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsSyncStatusBannerTest {
    @Test
    fun `cancellable catalog sync uses the non-modal banner`() {
        val state = SettingsUiState(isSyncing = true, syncCanCancel = true)

        assertThat(state.showsSyncBanner()).isTrue()
        assertThat(state.showsBlockingSyncOverlay()).isFalse()
    }

    @Test
    fun `short non-cancellable work keeps the blocking overlay`() {
        val state = SettingsUiState(isSyncing = true, syncCanCancel = false)

        assertThat(state.showsSyncBanner()).isFalse()
        assertThat(state.showsBlockingSyncOverlay()).isTrue()
    }

    @Test
    fun `idle settings show neither`() {
        val state = SettingsUiState(isSyncing = false, syncCanCancel = true)

        assertThat(state.showsSyncBanner()).isFalse()
        assertThat(state.showsBlockingSyncOverlay()).isFalse()
    }
}
