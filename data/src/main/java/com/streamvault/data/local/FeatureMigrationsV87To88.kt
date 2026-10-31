package com.streamvault.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.streamvault.domain.util.LiveChannelJunkClassifier
import com.streamvault.domain.util.LiveChannelJunkKind

/**
 * Persists the live-channel junk classification (`channels.junk_kind`) so counts and lists filter in SQL.
 * Existing rows are classified once, in id-keyed batches, inside the migration transaction.
 */
internal object FeatureMigrationsV87To88 {
    private const val BACKFILL_READ_BATCH = 5_000
    private const val BACKFILL_UPDATE_CHUNK = 500

    val MIGRATION_87_88 = object : Migration(87, 88) {
        override fun migrate(db: SupportSQLiteDatabase) {
            for (table in listOf("channels", "channel_import_stage")) {
                if (!tableHasColumn(db, table, "junk_kind")) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `junk_kind` INTEGER NOT NULL DEFAULT 0")
                }
            }
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_channels_provider_id_category_id_junk_kind` " +
                    "ON `channels` (`provider_id`, `category_id`, `junk_kind`)"
            )
            backfillChannelJunkKinds(db)
        }
    }

    /** Classifies every channel row; only non-NORMAL rows are written. Returns the number of rows updated. */
    internal fun backfillChannelJunkKinds(db: SupportSQLiteDatabase): Int {
        db.execSQL("UPDATE channels SET junk_kind = 0 WHERE junk_kind != 0")
        var lastId = -1L
        var updated = 0
        while (true) {
            val idsByKind = HashMap<Int, MutableList<Long>>()
            var rows = 0
            db.query(
                "SELECT id, name, category_name, group_title FROM channels " +
                    "WHERE id > $lastId ORDER BY id LIMIT $BACKFILL_READ_BATCH"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rows++
                    val id = cursor.getLong(0)
                    lastId = id
                    val categoryName = if (cursor.isNull(2)) null else cursor.getString(2)
                    val groupTitle = if (cursor.isNull(3)) null else cursor.getString(3)
                    val kind = LiveChannelJunkClassifier.classify(
                        cursor.getString(1).orEmpty(),
                        categoryName ?: groupTitle
                    )
                    if (kind != LiveChannelJunkKind.NORMAL) {
                        idsByKind.getOrPut(kind.code) { ArrayList() }.add(id)
                    }
                }
            }
            if (rows == 0) break
            for ((code, ids) in idsByKind) {
                for (chunk in ids.chunked(BACKFILL_UPDATE_CHUNK)) {
                    db.execSQL("UPDATE channels SET junk_kind = $code WHERE id IN (${chunk.joinToString(",")})")
                    updated += chunk.size
                }
            }
        }
        return updated
    }
}
