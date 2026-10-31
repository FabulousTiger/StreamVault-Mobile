package com.streamvault.data.sync.foreground

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.updateAndGet

/** Eager work goes foreground at once; lazy work on first real progress or its deadline. */
internal class SyncForegroundController(
    private val eager: Boolean,
    private val promote: suspend (SyncNotificationProgress) -> Boolean,
    private val update: (SyncNotificationProgress) -> Unit,
    private val nowMillis: () -> Long,
    private val lazyPromotionDelayMillis: Long,
    private val minUpdateIntervalMillis: Long = MIN_UPDATE_INTERVAL_MILLIS,
    private val promotionRetryMillis: Long = PROMOTION_RETRY_MILLIS
) {
    private sealed interface Event {
        data class Progress(val value: SyncNotificationProgress) : Event
        data object Deadline : Event
    }

    @Volatile
    var promoted: Boolean = false
        private set

    @Volatile
    var attempted: Boolean = false
        private set

    suspend fun run(initial: SyncNotificationProgress, progress: Flow<SyncNotificationProgress>) {
        var latest = initial
        var shown: SyncNotificationProgress? = null
        var lastAttemptAt: Long? = null

        suspend fun attemptPromotion() {
            val now = nowMillis()
            if (lastAttemptAt?.let { now - it < promotionRetryMillis } == true) return
            lastAttemptAt = now
            attempted = true
            if (promote(latest)) {
                promoted = true
                shown = latest
            }
        }

        if (eager) attemptPromotion()
        val deadline: Flow<Event> = if (eager) emptyFlow() else flow {
            delay(lazyPromotionDelayMillis)
            emit(Event.Deadline)
        }
        merge(progress.map { Event.Progress(it) }, deadline).conflate().collect { event ->
            if (event is Event.Progress) latest = event.value
            if (!promoted) {
                attemptPromotion()
            } else if (latest != shown) {
                update(latest)
                shown = latest
                delay(minUpdateIntervalMillis)
            }
        }
    }

    companion object {
        const val MIN_UPDATE_INTERVAL_MILLIS = 1_000L
        const val PROMOTION_RETRY_MILLIS = 30_000L
    }
}

/** Number of sync runs that tried to go foreground; drives the one-time background guidance. */
object SyncForegroundActivity {
    private val count = MutableStateFlow(0)
    private val mutableActive = MutableStateFlow(false)
    val active: StateFlow<Boolean> = mutableActive.asStateFlow()

    internal fun begin() {
        mutableActive.value = count.updateAndGet { it + 1 } > 0
    }

    internal fun end() {
        mutableActive.value = count.updateAndGet { (it - 1).coerceAtLeast(0) } > 0
    }
}
