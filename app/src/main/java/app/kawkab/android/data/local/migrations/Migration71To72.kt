package app.kawkab.android.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v71 → v72: remembers that a model's file was checked against its source.
 *
 * - `local_models.sourceSha256` — the SHA-256 the source published for the file, recorded when a
 *   download matched it. The Models screen tells a checked model from an unchecked one by comparing
 *   it with the file's current hash.
 *
 * Nullable and empty for every model registered before: none of their downloads was checked.
 */
val MIGRATION_71_72 = object : Migration(71, 72) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `local_models` ADD COLUMN `sourceSha256` TEXT")
    }
}
