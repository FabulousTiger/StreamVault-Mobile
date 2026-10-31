package com.streamvault.data.sync.foreground

import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncForegroundEntryPoint {
    fun syncForegroundProgressSource(): SyncForegroundProgressSource
}

/** Which provider a worker run belongs to and whether it should go foreground immediately. */
internal data class SyncForegroundPlan(
    val providerId: Long,
    val eager: Boolean,
    val initialStage: SyncNotificationStage,
    val lazyPromotionDelayMillis: Long
)

internal fun syncForegroundPlan(kind: SyncWorkKind, input: Data): SyncForegroundPlan {
    val providerId = input.getLong("provider_id", -1L)
    val automatic = input.getBoolean("automatic_refresh", false)
    return when (kind) {
        SyncWorkKind.PROVIDER -> SyncForegroundPlan(
            providerId = providerId.takeIf { it > 0L } ?: input.getLong("automatic_provider_id", -1L),
            eager = providerId > 0L,
            initialStage = SyncNotificationStage.PREPARING,
            lazyPromotionDelayMillis = LAZY_PROMOTION_DELAY_MILLIS
        )
        SyncWorkKind.XTREAM_INDEX, SyncWorkKind.STALKER_INDEX -> SyncForegroundPlan(
            providerId = providerId,
            eager = !automatic,
            initialStage = SyncNotificationStage.PREPARING,
            lazyPromotionDelayMillis = LAZY_PROMOTION_DELAY_MILLIS
        )
        SyncWorkKind.GUIDE -> SyncForegroundPlan(
            providerId = providerId,
            eager = input.getBoolean("force_refresh", false),
            initialStage = SyncNotificationStage.GUIDE,
            lazyPromotionDelayMillis = LAZY_PROMOTION_DELAY_MILLIS
        )
        SyncWorkKind.IN_PROCESS -> SyncForegroundPlan(
            providerId = -1L,
            eager = false,
            initialStage = SyncNotificationStage.PREPARING,
            lazyPromotionDelayMillis = IN_PROCESS_PROMOTION_DELAY_MILLIS
        )
    }
}

internal const val LAZY_PROMOTION_DELAY_MILLIS = 10_000L
internal const val IN_PROCESS_PROMOTION_DELAY_MILLIS = 3_000L

/** Runs [block] as dataSync foreground work when Android allows it; otherwise as plain background work. */
internal suspend fun CoroutineWorker.runWithSyncForeground(
    kind: SyncWorkKind,
    block: suspend () -> ListenableWorker.Result
): ListenableWorker.Result = withContext(SyncWorkerContext()) {
    val cancelGeneration = SyncCancellationRegistry.userCancelGeneration()
    try {
        val source = runCatching {
            EntryPointAccessors.fromApplication(applicationContext, SyncForegroundEntryPoint::class.java)
                .syncForegroundProgressSource()
        }.getOrNull()
        if (source == null) block() else runPromoted(kind, source, block)
    } catch (cancelled: CancellationException) {
        if (currentCoroutineContext().isActive && SyncCancellationRegistry.userCancelGeneration() != cancelGeneration) {
            Log.i(TAG, "Sync worker $id stopped from its notification")
            ListenableWorker.Result.success()
        } else {
            throw cancelled
        }
    }
}

private suspend fun CoroutineWorker.runPromoted(
    kind: SyncWorkKind,
    source: SyncForegroundProgressSource,
    block: suspend () -> ListenableWorker.Result
): ListenableWorker.Result = coroutineScope {
    val plan = syncForegroundPlan(kind, inputData)
    val notifications = SyncForegroundNotifications(applicationContext)
    val notificationId = SyncForegroundNotifications.notificationId(plan.providerId)
    var activityStarted = false
    val controller = SyncForegroundController(
        eager = plan.eager,
        promote = { progress ->
            if (!activityStarted) {
                activityStarted = true
                SyncForegroundActivity.begin()
            }
            try {
                setForeground(notifications.foregroundInfo(notificationId, progress))
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.i(TAG, "Sync worker $id stays in the background: ${error.javaClass.simpleName}")
                false
            }
        },
        update = { progress -> notifications.update(notificationId, progress) },
        nowMillis = System::currentTimeMillis,
        lazyPromotionDelayMillis = plan.lazyPromotionDelayMillis
    )
    val initial = SyncNotificationProgress(
        providerId = plan.providerId,
        providerName = source.providerName(plan.providerId),
        stage = plan.initialStage
    )
    val presenter = launch { controller.run(initial, source.observe(plan.providerId, kind)) }
    try {
        block()
    } finally {
        withContext(NonCancellable) {
            presenter.cancelAndJoin()
            if (activityStarted) SyncForegroundActivity.end()
        }
    }
}

private const val TAG = "SyncForeground"
