package com.streamvault.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.common.truth.Truth.assertThat
import com.streamvault.domain.util.LiveChannelJunkKind
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FeatureMigrationsV87To88Test {
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(87) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE channels (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "name TEXT NOT NULL, group_title TEXT, category_id INTEGER, category_name TEXT, " +
                                "provider_id INTEGER NOT NULL)"
                        )
                        db.execSQL("CREATE TABLE channel_import_stage (session_id INTEGER NOT NULL, name TEXT NOT NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        db = helper.writableDatabase
    }

    @After
    fun tearDown() = helper.close()

    private fun insert(id: Long, name: String, categoryName: String? = null, groupTitle: String? = null) {
        db.execSQL(
            "INSERT INTO channels (id, name, group_title, category_id, category_name, provider_id) " +
                "VALUES (?, ?, ?, 1, ?, 1)",
            arrayOf<Any?>(id, name, groupTitle, categoryName)
        )
    }

    private fun kinds(): Map<Long, Int> = buildMap {
        db.query("SELECT id, junk_kind FROM channels").use { c ->
            while (c.moveToNext()) put(c.getLong(0), c.getInt(1))
        }
    }

    @Test
    fun `migration adds columns index and backfills existing rows`() {
        insert(1, "CNN")
        insert(2, "##### NEWS #####")
        insert(3, "Friends 24/7")
        insert(4, "Hits FM", categoryName = "UK | RADIO")
        insert(5, "Gradio Fan", groupTitle = "Music")
        insert(6, "Mystery", categoryName = null, groupTitle = "24/7 Movies")

        FeatureMigrationsV87To88.MIGRATION_87_88.migrate(db)

        assertThat(tableHasColumn(db, "channels", "junk_kind")).isTrue()
        assertThat(tableHasColumn(db, "channel_import_stage", "junk_kind")).isTrue()
        assertThat(kinds()).containsExactly(
            1L, LiveChannelJunkKind.NORMAL.code,
            2L, LiveChannelJunkKind.HEADER.code,
            3L, LiveChannelJunkKind.LOOP_24_7.code,
            4L, LiveChannelJunkKind.RADIO.code,
            5L, LiveChannelJunkKind.NORMAL.code,
            6L, LiveChannelJunkKind.LOOP_24_7.code
        )
        db.query("PRAGMA index_list(channels)").use { c ->
            val names = buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
            assertThat(names).contains("index_channels_provider_id_category_id_junk_kind")
        }
    }

    @Test
    fun `backfill is idempotent and spans several read batches`() {
        db.beginTransaction()
        for (id in 1L..12_000L) insert(id, if (id % 1000 == 0L) "=== H$id ===" else "Channel $id")
        db.setTransactionSuccessful()
        db.endTransaction()

        FeatureMigrationsV87To88.MIGRATION_87_88.migrate(db)
        val first = kinds()
        FeatureMigrationsV87To88.MIGRATION_87_88.migrate(db)

        assertThat(kinds()).isEqualTo(first)
        assertThat(first.values.count { it == LiveChannelJunkKind.HEADER.code }).isEqualTo(12)
        assertThat(first.values.count { it == LiveChannelJunkKind.NORMAL.code }).isEqualTo(11_988)
    }
}
