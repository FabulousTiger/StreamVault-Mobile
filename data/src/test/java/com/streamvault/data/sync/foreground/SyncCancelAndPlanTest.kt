package com.streamvault.data.sync.foreground

import android.content.Context
import androidx.work.workDataOf
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SyncCancelAndPlanTest {
    private val context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() = SyncCancellationRegistry.resetForTest()

    @Test
    fun `cancel stops every one-time chain of the provider and pauses automatic checks`() {
        val cancelled = mutableListOf<String>()
        val pause = SyncUserPauseStore(context)

        cancelProviderSync(7L, cancelUniqueWork = { cancelled += it }, pause = pause, nowMillis = 1_000L)

        assertThat(cancelled).containsExactly(
            "provider-workflow-7",
            "provider-automatic-index-7",
            "provider-stalker-index-7",
            "provider-epg-7",
            "provider-automatic-check-7"
        )
        assertThat(cancelled.none { it == "provider-sync-worker" }).isTrue()
        assertThat(pause.isPaused(7L, nowMillis = 1_000L + SyncUserPauseStore.PAUSE_MILLIS - 1)).isTrue()
        assertThat(pause.isPaused(7L, nowMillis = 1_000L + SyncUserPauseStore.PAUSE_MILLIS)).isFalse()
        assertThat(pause.isPaused(8L, nowMillis = 1_000L)).isFalse()
    }

    @Test
    fun `one failing cancellation does not skip the others`() {
        val cancelled = mutableListOf<String>()

        cancelProviderSync(7L, cancelUniqueWork = { name ->
            if (name == "provider-workflow-7") error("WorkManager unavailable")
            cancelled += name
        }, pause = null)

        assertThat(cancelled).hasSize(4)
    }

    @Test
    fun `pause window rejects timestamps beyond the window after a clock change`() {
        assertThat(isAutomaticSyncPaused(pausedUntilMillis = 0L, nowMillis = 10L)).isFalse()
        assertThat(isAutomaticSyncPaused(pausedUntilMillis = 20L, nowMillis = 10L)).isTrue()
        assertThat(
            isAutomaticSyncPaused(pausedUntilMillis = 10L + SyncUserPauseStore.PAUSE_MILLIS + 1, nowMillis = 10L)
        ).isFalse()
    }

    @Test
    fun `pause store clear resumes automatic checks`() {
        val pause = SyncUserPauseStore(context)
        pause.pause(9L, nowMillis = 0L)

        pause.clear(9L)

        assertThat(pause.isPaused(9L, nowMillis = 1L)).isFalse()
        assertThat(isAutomaticSyncPausedByUser(context, 9L)).isFalse()
    }

    @Test
    fun `explicit provider work is eager while periodic checks stay lazy`() {
        val targeted = syncForegroundPlan(SyncWorkKind.PROVIDER, workDataOf("provider_id" to 7L))
        val periodic = syncForegroundPlan(SyncWorkKind.PROVIDER, workDataOf())
        val automatic = syncForegroundPlan(SyncWorkKind.PROVIDER, workDataOf("automatic_provider_id" to 8L))

        assertThat(targeted.eager).isTrue()
        assertThat(targeted.providerId).isEqualTo(7L)
        assertThat(periodic.eager).isFalse()
        assertThat(periodic.providerId).isEqualTo(-1L)
        assertThat(automatic.eager).isFalse()
        assertThat(automatic.providerId).isEqualTo(8L)
    }

    @Test
    fun `manual index and forced guide work are eager, automatic work is lazy`() {
        val manualIndex = syncForegroundPlan(SyncWorkKind.XTREAM_INDEX, workDataOf("provider_id" to 7L))
        val automaticIndex = syncForegroundPlan(
            SyncWorkKind.STALKER_INDEX,
            workDataOf("provider_id" to 7L, "automatic_refresh" to true)
        )
        val forcedGuide = syncForegroundPlan(SyncWorkKind.GUIDE, workDataOf("provider_id" to 7L, "force_refresh" to true))
        val scheduledGuide = syncForegroundPlan(SyncWorkKind.GUIDE, workDataOf("provider_id" to 7L))
        val keepAlive = syncForegroundPlan(SyncWorkKind.IN_PROCESS, workDataOf())

        assertThat(manualIndex.eager).isTrue()
        assertThat(automaticIndex.eager).isFalse()
        assertThat(forcedGuide.eager).isTrue()
        assertThat(forcedGuide.initialStage).isEqualTo(SyncNotificationStage.GUIDE)
        assertThat(scheduledGuide.eager).isFalse()
        assertThat(keepAlive.eager).isFalse()
        assertThat(keepAlive.lazyPromotionDelayMillis).isLessThan(scheduledGuide.lazyPromotionDelayMillis)
    }

    @Test
    fun `receiver ignores foreign actions and invalid providers`() {
        val receiver = SyncCancelReceiver()

        receiver.onReceive(context, android.content.Intent("other"))
        receiver.onReceive(context, SyncCancelReceiver.intent(context, -1L))

        assertThat(SyncUserPauseStore(context).isPaused(-1L)).isFalse()
    }
}
