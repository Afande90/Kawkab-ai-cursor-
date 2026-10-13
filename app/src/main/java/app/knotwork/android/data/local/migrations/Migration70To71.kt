package app.knotwork.android.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v70 → v71: remembers where each downloaded model came from.
 *
 * - `local_models.sourceUrl` — the address the file was downloaded from. A backup carries the model
 *   list, not the files, so a restored phone can offer each model for download again only if it knows
 *   where to fetch it.
 *
 * Nullable and empty for every model registered before: their source was never recorded, and a model
 * found on disk at start-up has none.
 */
val MIGRATION_70_71 = object : Migration(70, 71) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `local_models` ADD COLUMN `sourceUrl` TEXT")
    }
}
