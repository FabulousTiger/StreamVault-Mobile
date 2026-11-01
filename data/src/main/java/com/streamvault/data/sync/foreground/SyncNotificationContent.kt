package com.streamvault.data.sync.foreground

import android.content.res.Resources
import com.streamvault.data.R
import com.streamvault.data.local.entity.XtreamIndexJobEntity
import com.streamvault.domain.sync.Section
import com.streamvault.domain.sync.SyncProgress
import java.text.NumberFormat
import java.util.Locale

/** Catalog area shown in a sync notification. */
enum class SyncNotificationStage { PREPARING, LIVE, MOVIES, SERIES, GUIDE }

/** Structured, locale-free notification state; text is produced by [formatSyncNotification]. */
data class SyncNotificationProgress(
    val providerId: Long,
    val providerName: String?,
    val stage: SyncNotificationStage,
    val processed: Int = 0,
    val total: Int = 0
)

internal data class SyncNotificationText(
    val title: String,
    val text: String,
    val progressMax: Int,
    val progressValue: Int,
    val indeterminate: Boolean
)

internal interface SyncNotificationStrings {
    fun fallbackTitle(): String
    fun preparing(): String
    fun section(stage: SyncNotificationStage): String
    fun count(section: String, count: String): String
    fun total(section: String, count: String, total: String): String
    fun working(section: String): String
}

internal class ResourceSyncNotificationStrings(private val resources: Resources) : SyncNotificationStrings {
    override fun fallbackTitle(): String = resources.getString(R.string.sync_fg_title_fallback)
    override fun preparing(): String = resources.getString(R.string.sync_fg_preparing)
    override fun section(stage: SyncNotificationStage): String = resources.getString(
        when (stage) {
            SyncNotificationStage.LIVE -> R.string.sync_fg_section_live
            SyncNotificationStage.MOVIES -> R.string.sync_fg_section_movies
            SyncNotificationStage.SERIES -> R.string.sync_fg_section_series
            SyncNotificationStage.GUIDE -> R.string.sync_fg_section_guide
            SyncNotificationStage.PREPARING -> R.string.sync_fg_preparing
        }
    )
    override fun count(section: String, count: String): String =
        resources.getString(R.string.sync_fg_progress_count, section, count)
    override fun total(section: String, count: String, total: String): String =
        resources.getString(R.string.sync_fg_progress_total, section, count, total)
    override fun working(section: String): String = resources.getString(R.string.sync_fg_section_working, section)
}

internal fun formatSyncNotification(
    progress: SyncNotificationProgress,
    strings: SyncNotificationStrings,
    locale: Locale
): SyncNotificationText {
    val title = progress.providerName?.takeIf { it.isNotBlank() } ?: strings.fallbackTitle()
    if (progress.stage == SyncNotificationStage.PREPARING) {
        return SyncNotificationText(title, strings.preparing(), 0, 0, indeterminate = true)
    }
    val numbers = NumberFormat.getIntegerInstance(locale)
    val processed = progress.processed.coerceAtLeast(0)
    val total = progress.total.takeIf { it > 0 && it >= processed } ?: 0
    val section = strings.section(progress.stage)
    val text = when {
        total > 0 -> strings.total(section, numbers.format(processed), numbers.format(total))
        processed > 0 -> strings.count(section, numbers.format(processed))
        else -> strings.working(section)
    }
    return SyncNotificationText(
        title = title,
        text = text,
        progressMax = total,
        progressValue = if (total > 0) processed else 0,
        indeterminate = total == 0
    )
}

/** Maps bus progress (cumulative itemsIndexed, rebased per section) and index jobs to notification state. */
internal class SyncNotificationProgressMapper(
    private val providerId: Long,
    private val providerName: String?,
    private val expectedTotal: (SyncNotificationStage) -> Int
) {
    private var currentSection: Section? = null
    private var sectionBaseline = 0
    private var lastItemsIndexed = 0

    fun map(bus: SyncProgress?, jobs: List<XtreamIndexJobEntity>): SyncNotificationProgress? {
        if (bus != null) {
            if (bus.section != currentSection) {
                sectionBaseline = if (currentSection == null) 0 else lastItemsIndexed
                currentSection = bus.section
            }
            lastItemsIndexed = bus.itemsIndexed
            val stage = bus.section.toStage()
            return SyncNotificationProgress(
                providerId = providerId,
                providerName = providerName,
                stage = stage,
                processed = (bus.itemsIndexed - sectionBaseline).coerceAtLeast(0),
                total = expectedTotal(stage)
            )
        }
        val running = jobs
            .filter { it.state == "RUNNING" && it.section in INDEX_SECTIONS }
            .maxByOrNull { it.updatedAt }
            ?: return null
        val stage = indexSectionStage(running.section)
        return SyncNotificationProgress(providerId, providerName, stage, running.indexedRows, expectedTotal(stage))
    }

    companion object {
        val INDEX_SECTIONS = setOf("LIVE", "MOVIE", "SERIES")
    }
}

internal fun indexSectionStage(section: String): SyncNotificationStage = when (section) {
    "LIVE" -> SyncNotificationStage.LIVE
    "MOVIE" -> SyncNotificationStage.MOVIES
    "SERIES" -> SyncNotificationStage.SERIES
    else -> SyncNotificationStage.PREPARING
}

private fun Section.toStage(): SyncNotificationStage = when (this) {
    Section.LIVE -> SyncNotificationStage.LIVE
    Section.VOD -> SyncNotificationStage.MOVIES
    Section.SERIES -> SyncNotificationStage.SERIES
}
