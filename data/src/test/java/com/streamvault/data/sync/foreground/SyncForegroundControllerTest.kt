package com.streamvault.data.sync.foreground

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncForegroundControllerTest {
    private val initial = SyncNotificationProgress(7L, "Strong", SyncNotificationStage.PREPARING)
    private fun movies(rows: Int) = SyncNotificationProgress(7L, "Strong", SyncNotificationStage.MOVIES, rows)

    private class Recorder(var allowPromotion: Boolean = true) {
        val promotions = mutableListOf<SyncNotificationProgress>()
        val updates = mutableListOf<SyncNotificationProgress>()
    }

    private fun TestScope.controller(recorder: Recorder, eager: Boolean) = SyncForegroundController(
        eager = eager,
        promote = { progress ->
            recorder.promotions += progress
            recorder.allowPromotion
        },
        update = { recorder.updates += it },
        nowMillis = { testScheduler.currentTime },
        lazyPromotionDelayMillis = 10_000L
    )

    @Test
    fun `eager work is promoted before any progress arrives`() = runTest {
        val recorder = Recorder()
        val controller = controller(recorder, eager = true)

        val job = launch { controller.run(initial, emptyFlow()) }
        runCurrent()

        assertThat(recorder.promotions).containsExactly(initial)
        assertThat(controller.promoted).isTrue()
        job.cancel()
    }

    @Test
    fun `lazy work without progress is promoted only at its deadline`() = runTest {
        val recorder = Recorder()
        val progress = MutableSharedFlow<SyncNotificationProgress>()
        val controller = controller(recorder, eager = false)

        val job = launch { controller.run(initial, progress) }
        advanceTimeBy(9_999L)
        assertThat(recorder.promotions).isEmpty()
        advanceTimeBy(2L)

        assertThat(recorder.promotions).containsExactly(initial)
        job.cancel()
    }

    @Test
    fun `lazy work whose progress stream completes is still promoted at its deadline`() = runTest {
        val recorder = Recorder()
        val controller = controller(recorder, eager = false)

        val job = launch { controller.run(initial, emptyFlow()) }
        advanceTimeBy(10_001L)

        assertThat(recorder.promotions).containsExactly(initial)
        assertThat(controller.promoted).isTrue()
        job.cancel()
    }

    @Test
    fun `lazy work is promoted by its first real progress`() = runTest {
        val recorder = Recorder()
        val progress = MutableSharedFlow<SyncNotificationProgress>()
        val controller = controller(recorder, eager = false)

        val job = launch { controller.run(initial, progress) }
        runCurrent()
        progress.emit(movies(10))
        runCurrent()

        assertThat(recorder.promotions).containsExactly(movies(10))
        job.cancel()
    }

    @Test
    fun `refused promotion is retried only after the retry window and never updates`() = runTest {
        val recorder = Recorder(allowPromotion = false)
        val progress = MutableSharedFlow<SyncNotificationProgress>()
        val controller = controller(recorder, eager = true)

        val job = launch { controller.run(initial, progress) }
        runCurrent()
        progress.emit(movies(10))
        runCurrent()
        assertThat(recorder.promotions).hasSize(1)

        advanceTimeBy(SyncForegroundController.PROMOTION_RETRY_MILLIS)
        recorder.allowPromotion = true
        progress.emit(movies(20))
        runCurrent()

        assertThat(recorder.promotions).containsExactly(initial, movies(20)).inOrder()
        assertThat(recorder.updates).isEmpty()
        assertThat(controller.promoted).isTrue()
        job.cancel()
    }

    @Test
    fun `updates are throttled and the latest value wins`() = runTest {
        val recorder = Recorder()
        val progress = MutableSharedFlow<SyncNotificationProgress>(extraBufferCapacity = 16)
        val controller = controller(recorder, eager = true)

        val job = launch { controller.run(initial, progress) }
        runCurrent()
        progress.emit(movies(1))
        runCurrent()
        progress.emit(movies(2))
        progress.emit(movies(3))
        runCurrent()
        assertThat(recorder.updates).containsExactly(movies(1))

        advanceTimeBy(SyncForegroundController.MIN_UPDATE_INTERVAL_MILLIS + 1)

        assertThat(recorder.updates).containsExactly(movies(1), movies(3)).inOrder()
        job.cancel()
    }

    @Test
    fun `unchanged progress does not repost the notification`() = runTest {
        val recorder = Recorder()
        val progress = MutableSharedFlow<SyncNotificationProgress>(extraBufferCapacity = 4)
        val controller = controller(recorder, eager = true)

        val job = launch { controller.run(initial, progress) }
        runCurrent()
        progress.emit(initial)
        runCurrent()

        assertThat(recorder.updates).isEmpty()
        job.cancel()
    }
}
