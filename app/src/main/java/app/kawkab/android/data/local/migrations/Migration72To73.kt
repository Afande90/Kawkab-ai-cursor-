package app.kawkab.android.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v72 → v73: records a queue item that failed without ending its run.
 *
 * - `trace_steps.failure` — the redacted error of a `NODE_IO` row whose visit failed while its queue
 *   carried on (`stopOnError` off). A checkpoint resume replays it as the item's result; without it,
 *   the resume matched the failed visit to the next item's row.
 *
 * Nullable and empty for every row written before: none of them recorded a failure.
 */
val MIGRATION_72_73 = object : Migration(72, 73) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `trace_steps` ADD COLUMN `failure` TEXT")
    }
}
