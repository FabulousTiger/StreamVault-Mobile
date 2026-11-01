package com.streamvault.data.sync.foreground

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

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
    @Volatile
    var promoted: Boolean = false
        private set

    @Volatile
    var attempted: Boolean = false
        private set

    /** Runs until cancelled; progress arriving during the update pause collapses into its latest value. */
    suspend fun run(initial: SyncNotificationProgress, progress: Flow<SyncNotificationProgress>) {
        coroutineScope {
            val latest = AtomicReference(initial)
            val wakeUps = Channel<Unit>(Channel.CONFLATED)
            var shown: SyncNotificationProgress? = null
            var lastAttemptAt: Long? = null

            suspend fun attemptPromotion() {
                val now = nowMillis()
                if (lastAttemptAt?.let { now - it < promotionRetryMillis } == true) return
                lastAttemptAt = now
                attempted = true
                val candidate = latest.get()
                if (promote(candidate)) {
                    promoted = true
                    shown = candidate
                }
            }

            if (eager) attemptPromotion()
            launch {
                progress.collect { value ->
                    latest.set(value)
                    wakeUps.trySend(Unit)
                }
            }
            if (!eager) {
                launch {
                    delay(lazyPromotionDelayMillis)
                    wakeUps.trySend(Unit)
                }
            }
            while (true) {
                wakeUps.receive()
                val current = latest.get()
                if (!promoted) {
                    attemptPromotion()
                } else if (current != shown) {
                    update(current)
                    shown = current
                    delay(minUpdateIntervalMillis)
                }
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
