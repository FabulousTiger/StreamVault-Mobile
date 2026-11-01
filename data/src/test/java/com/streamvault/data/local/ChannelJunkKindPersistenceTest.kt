package com.streamvault.data.local

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.streamvault.data.local.entity.ChannelEntity
import com.streamvault.data.local.entity.ChannelImportStageEntity
import com.streamvault.data.local.entity.ProviderConfigEntity
import com.streamvault.data.local.entity.ProviderEntity
import com.streamvault.domain.model.ProviderType
import com.streamvault.domain.util.LiveChannelJunkKind
import com.streamvault.domain.util.LiveJunkFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ChannelJunkKindPersistenceTest {
    private lateinit var database: StreamVaultDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            StreamVaultDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private suspend fun seed() {
        database.providerDao().insert(ProviderEntity(id = 7, name = "P", type = ProviderType.M3U))
        database.providerSnapshotDao().commitConfiguration(
            ProviderConfigEntity(
                providerId = 7,
                type = ProviderType.M3U,
                schemaVersion = 1,
                configurationGeneration = 1,
                identityKey = "identity",
                encryptedConfigJson = "{}",
                updatedAt = 1
            )
        )
        database.channelDao().insertAll(
            listOf(
                channel(1, "CNN", 10),
                channel(2, "Sky Sports 24", 10),
                channel(3, "----- NL -----", 10),
                channel(4, "Friends 24/7", 20),
                channel(5, "Hits FM", 20, categoryName = "UK | RADIO"),
                channel(6, "BBC One", 20, logicalGroupId = "bbc"),
                channel(7, "BBC One HD", 20, logicalGroupId = "bbc")
            )
        )
    }

    private fun channel(
        id: Long,
        name: String,
        categoryId: Long,
        categoryName: String? = null,
        logicalGroupId: String = ""
    ) = ChannelEntity(
        id = id,
        streamId = id,
        name = name,
        number = id.toInt(),
        categoryId = categoryId,
        categoryName = categoryName,
        providerId = 7,
        logicalGroupId = logicalGroupId
    )

    @Test
    fun `new channel rows persist the classifier result`() = runTest {
        seed()

        val kinds = database.channelDao().getByProviderSync(7).associate { it.id to it.junkKind }

        assertThat(kinds).containsExactly(
            1L, LiveChannelJunkKind.NORMAL.code,
            2L, LiveChannelJunkKind.NORMAL.code,
            3L, LiveChannelJunkKind.HEADER.code,
            4L, LiveChannelJunkKind.LOOP_24_7.code,
            5L, LiveChannelJunkKind.RADIO.code,
            6L, LiveChannelJunkKind.NORMAL.code,
            7L, LiveChannelJunkKind.NORMAL.code
        )
    }

    @Test
    fun `sql counts follow the toggles`() = runTest {
        seed()
        val dao = database.channelDao()
        val all = LiveJunkFilter(true, true, true).hiddenKindCodes()
        val headersOnly = LiveJunkFilter(hideHeaders = true).hiddenKindCodes()
        val loopsAndRadio = LiveJunkFilter(hideLoops = true, hideRadio = true).hiddenKindCodes()

        assertThat(dao.getCountExcluding(7, all).first()).isEqualTo(4)
        assertThat(dao.getCountExcluding(7, headersOnly).first()).isEqualTo(6)
        assertThat(dao.getCountExcluding(7, loopsAndRadio).first()).isEqualTo(5)
        assertThat(dao.getCountExcluding(7, emptyList()).first()).isEqualTo(7)

        assertThat(dao.getCategoryCountsExcluding(7, all).first().associate { it.categoryId to it.item_count })
            .containsExactly(10L, 2, 20L, 2)
        assertThat(dao.getGroupedCategoryCountsExcluding(7, all).first().associate { it.categoryId to it.item_count })
            .containsExactly(10L, 2, 20L, 1)
    }

    @Test
    fun `sql pages skip hidden kinds so pages stay full`() = runTest {
        seed()
        val dao = database.channelDao()
        val all = LiveJunkFilter(true, true, true).hiddenKindCodes()

        val page = dao.getByProviderBrowsePageOffset(7, limit = 3, offset = 0, hiddenKinds = all)
        val category = dao.getByCategoryBrowsePageOffset(7, 20, limit = 5, offset = 0, hiddenKinds = all)
        val flowPage = dao.getByProviderBrowsePage(7, limit = 10, hiddenKinds = listOf(1)).first()

        assertThat(page.map { it.id }).containsExactly(1L, 2L, 6L).inOrder()
        assertThat(category.map { it.id }).containsExactly(6L, 7L).inOrder()
        assertThat(flowPage.map { it.id }).doesNotContain(3L)
        assertThat(flowPage).hasSize(6)
    }

    @Test
    fun `stage reconciliation inserts and updates junk_kind`() = runTest {
        database.providerDao().insert(ProviderEntity(id = 7, name = "P", type = ProviderType.M3U))
        val sync = database.catalogSyncDao()
        sync.insertChannelStages(
            listOf(stage(1, "CNN"), stage(2, "=== NEWS ==="))
        )
        sync.insertMissingChannelsFromStage(7, SESSION)

        assertThat(database.channelDao().getByProviderSync(7).associate { it.streamId to it.junkKind })
            .containsExactly(1L, 0, 2L, LiveChannelJunkKind.HEADER.code)

        sync.insertChannelStages(listOf(stage(1, "Friends 24/7", fingerprint = "changed")))
        sync.updateChangedChannelsFromStage(7, SESSION)

        assertThat(database.channelDao().getByProviderSync(7).first { it.streamId == 1L }.junkKind)
            .isEqualTo(LiveChannelJunkKind.LOOP_24_7.code)
    }

    private fun stage(streamId: Long, name: String, fingerprint: String = "fp") = ChannelImportStageEntity(
        sessionId = SESSION,
        providerId = 7,
        streamId = streamId,
        name = name,
        categoryId = 10,
        syncFingerprint = fingerprint
    )

    private companion object {
        const val SESSION = 99L
    }
}
