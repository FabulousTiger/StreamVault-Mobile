package com.streamvault.data.sync.foreground

import com.google.common.truth.Truth.assertThat
import com.streamvault.data.local.entity.XtreamIndexJobEntity
import com.streamvault.domain.sync.Section
import com.streamvault.domain.sync.SyncProgress
import java.util.Locale
import org.junit.Test

class SyncNotificationContentTest {
    private val strings = object : SyncNotificationStrings {
        override fun fallbackTitle() = "Updating catalog"
        override fun preparing() = "Preparing"
        override fun section(stage: SyncNotificationStage) = stage.name.lowercase()
        override fun count(section: String, count: String) = "$section: $count"
        override fun total(section: String, count: String, total: String) = "$section: $count / $total"
        override fun working(section: String) = "$section: updating"
    }

    @Test
    fun `known total shows processed and expected count with grouping`() {
        val text = formatSyncNotification(
            SyncNotificationProgress(7L, "Strong", SyncNotificationStage.MOVIES, 120_000, 400_000),
            strings,
            Locale.US
        )

        assertThat(text.title).isEqualTo("Strong")
        assertThat(text.text).isEqualTo("movies: 120,000 / 400,000")
        assertThat(text.indeterminate).isFalse()
        assertThat(text.progressMax).isEqualTo(400_000)
        assertThat(text.progressValue).isEqualTo(120_000)
    }

    @Test
    fun `numbers follow the notification locale`() {
        val text = formatSyncNotification(
            SyncNotificationProgress(7L, "Strong", SyncNotificationStage.MOVIES, 120_000, 400_000),
            strings,
            Locale.GERMANY
        )

        assertThat(text.text).isEqualTo("movies: 120.000 / 400.000")
    }

    @Test
    fun `a stale total smaller than the processed count is dropped`() {
        val text = formatSyncNotification(
            SyncNotificationProgress(7L, "Strong", SyncNotificationStage.LIVE, 60_000, 56_000),
            strings,
            Locale.US
        )

        assertThat(text.text).isEqualTo("live: 60,000")
        assertThat(text.indeterminate).isTrue()
        assertThat(text.progressMax).isEqualTo(0)
    }

    @Test
    fun `no count yet shows the section as updating`() {
        val text = formatSyncNotification(
            SyncNotificationProgress(7L, "Strong", SyncNotificationStage.GUIDE),
            strings,
            Locale.US
        )

        assertThat(text.text).isEqualTo("guide: updating")
    }

    @Test
    fun `preparing without provider name uses fallback title`() {
        val text = formatSyncNotification(
            SyncNotificationProgress(-1L, " ", SyncNotificationStage.PREPARING),
            strings,
            Locale.US
        )

        assertThat(text.title).isEqualTo("Updating catalog")
        assertThat(text.text).isEqualTo("Preparing")
        assertThat(text.indeterminate).isTrue()
    }

    @Test
    fun `cumulative bus counts are rebased when the section changes`() {
        val mapper = SyncNotificationProgressMapper(7L, "Strong") { 0 }

        val live = mapper.map(SyncProgress(Section.LIVE, 0, 0, "", 56_000), emptyList())
        val movies = mapper.map(SyncProgress(Section.VOD, 0, 0, "", 56_000), emptyList())
        val moreMovies = mapper.map(SyncProgress(Section.VOD, 0, 0, "", 56_500), emptyList())

        assertThat(live?.stage).isEqualTo(SyncNotificationStage.LIVE)
        assertThat(live?.processed).isEqualTo(56_000)
        assertThat(movies?.stage).isEqualTo(SyncNotificationStage.MOVIES)
        assertThat(movies?.processed).isEqualTo(0)
        assertThat(moreMovies?.processed).isEqualTo(500)
    }

    @Test
    fun `a section-only session counts from zero`() {
        val mapper = SyncNotificationProgressMapper(7L, null) { 0 }

        val series = mapper.map(SyncProgress(Section.SERIES, 0, 0, "", 900), emptyList())

        assertThat(series?.processed).isEqualTo(900)
    }

    @Test
    fun `running index job supplies rows and the remembered total`() {
        val mapper = SyncNotificationProgressMapper(7L, "Strong") { stage ->
            if (stage == SyncNotificationStage.MOVIES) 400_000 else 0
        }
        val jobs = listOf(
            XtreamIndexJobEntity(providerId = 7L, section = "SERIES", state = "QUEUED", indexedRows = 10),
            XtreamIndexJobEntity(providerId = 7L, section = "MOVIE", state = "RUNNING", indexedRows = 120_000),
            XtreamIndexJobEntity(providerId = 7L, section = "EPG", state = "RUNNING")
        )

        val progress = mapper.map(null, jobs)

        assertThat(progress).isEqualTo(
            SyncNotificationProgress(7L, "Strong", SyncNotificationStage.MOVIES, 120_000, 400_000)
        )
    }

    @Test
    fun `nothing running maps to no notification update`() {
        val mapper = SyncNotificationProgressMapper(7L, "Strong") { 0 }

        val progress = mapper.map(
            null,
            listOf(XtreamIndexJobEntity(providerId = 7L, section = "MOVIE", state = "SUCCESS", indexedRows = 5))
        )

        assertThat(progress).isNull()
    }
}
