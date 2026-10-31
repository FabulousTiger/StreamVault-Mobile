package com.streamvault.data.sync.foreground

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.streamvault.data.local.dao.ProviderDao
import com.streamvault.data.local.dao.XtreamIndexJobDao
import com.streamvault.data.local.entity.XtreamIndexJobEntity
import com.streamvault.data.sync.SyncProgressBus
import com.streamvault.domain.repository.SyncMetadataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach

/** Kind of sync worker that owns a notification. */
enum class SyncWorkKind { PROVIDER, XTREAM_INDEX, STALKER_INDEX, GUIDE, IN_PROCESS }

/** Live notification state for a worker, derived from the progress bus and durable index jobs. */
@Singleton
class SyncForegroundProgressSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val providerDao: ProviderDao,
    private val progressBus: SyncProgressBus,
    private val xtreamIndexJobDao: XtreamIndexJobDao,
    private val syncMetadataRepository: SyncMetadataRepository
) {
    private val totals by lazy { SyncExpectedTotalsStore(context) }

    suspend fun providerName(providerId: Long): String? =
        if (providerId > 0L) runCatching { providerDao.getById(providerId)?.name }.getOrNull() else null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(providerId: Long, kind: SyncWorkKind): Flow<SyncNotificationProgress> {
        val providers: Flow<Long?> = if (providerId > 0L) {
            flowOf(providerId)
        } else {
            SyncCancellationRegistry.active.map { active ->
                active.firstOrNull { kind != SyncWorkKind.IN_PROCESS || it.inProcess }?.providerId
            }
        }
        return providers.distinctUntilChanged().flatMapLatest { id ->
            if (id == null || kind == SyncWorkKind.GUIDE) emptyFlow() else observeProvider(id, kind)
        }
    }

    private fun observeProvider(providerId: Long, kind: SyncWorkKind): Flow<SyncNotificationProgress> = flow {
        val name = providerName(providerId)
        val metadata = runCatching { syncMetadataRepository.getMetadata(providerId) }.getOrNull()
        val expectedLive = metadata?.takeIf { it.lastLiveSuccess > 0L }?.liveCount ?: 0
        val mapper = SyncNotificationProgressMapper(providerId, name) { stage ->
            if (stage == SyncNotificationStage.LIVE) expectedLive else totals.get(providerId, stage)
        }
        val jobs: Flow<List<XtreamIndexJobEntity>> = if (kind == SyncWorkKind.STALKER_INDEX) {
            flowOf(emptyList())
        } else {
            xtreamIndexJobDao.observeForProvider(providerId).onEach { all ->
                all.filter { it.state == "SUCCESS" && it.indexedRows > 0 }.forEach { job ->
                    totals.record(providerId, indexSectionStage(job.section), job.indexedRows)
                }
            }
        }
        val bus = progressBus.progressByProvider.map { it[providerId]?.progress }.distinctUntilChanged()
        emitAll(combine(bus, jobs) { progress, indexJobs -> progress to indexJobs }
            .mapNotNull { (progress, indexJobs) -> mapper.map(progress, indexJobs) }
            .distinctUntilChanged())
    }
}

/** Last completed row count per provider section, used as the "/ total" of the next run. */
internal class SyncExpectedTotalsStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("sync_notification_totals", Context.MODE_PRIVATE)
    )

    fun get(providerId: Long, stage: SyncNotificationStage): Int =
        preferences.getInt(key(providerId, stage), 0)

    fun record(providerId: Long, stage: SyncNotificationStage, count: Int) {
        if (stage == SyncNotificationStage.PREPARING || count <= 0 || get(providerId, stage) == count) return
        preferences.edit { putInt(key(providerId, stage), count) }
    }

    private fun key(providerId: Long, stage: SyncNotificationStage) = "${providerId}_${stage.name}"
}
