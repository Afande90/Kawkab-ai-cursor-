package app.knotwork.android.data.backup

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.migrations.ALL_MIGRATIONS
import app.knotwork.android.domain.backup.BackupRestoreException
import app.knotwork.android.domain.backup.RestoreFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes a staged database ready to replace the app's own.
 *
 * @see SqlCipherStagedDatabasePreparer
 */
internal interface StagedDatabasePreparer {

    /**
     * Checks the database at [file], keys it for this phone and brings it to this build's schema.
     *
     * @param file The database copy from the archive, in the staging area.
     * @param archiveKey The 32 bytes the copy is encrypted with, from the archive.
     * @param phoneKey This phone's database passphrase; the copy is keyed with it.
     * @return The schema version the database was at in the archive.
     * @throws BackupRestoreException [RestoreFailure.NEWER_VERSION] for a database of a later schema, and
     *   [RestoreFailure.DATABASE_UNUSABLE] for one that does not open, fails a check or cannot be migrated.
     */
    fun prepare(file: File, archiveKey: ByteArray, phoneKey: ByteArray): Int
}

/**
 * [StagedDatabasePreparer] on SQLCipher and Room.
 *
 * The copy is opened under the archive's key and checked page by page (`cipher_integrity_check`) and as a
 * database (`quick_check`); its `user_version` must not be above [AppDatabase.SCHEMA_VERSION] — Room would
 * recreate a newer database empty rather than downgrade it. It is then re-keyed with this phone's passphrase
 * (`changePassword`, which reports a failure), and opened once through Room with every migration: an older
 * database is brought up to date and every database is checked against this build's schema here, in
 * staging, before anything of the app's is replaced.
 *
 * Every statement goes through a call that reports a failed step (`RawExecSqlGuardTest`). A reported
 * corruption only logs: the stock handler would delete files.
 */
@Singleton
internal class SqlCipherStagedDatabasePreparer @Inject constructor(@ApplicationContext private val context: Context) :
    StagedDatabasePreparer {

    override fun prepare(file: File, archiveKey: ByteArray, phoneKey: ByteArray): Int {
        System.loadLibrary(NATIVE_LIBRARY)
        val version = try {
            checkAndRekey(file, archiveKey, phoneKey)
        } catch (e: RuntimeException) {
            throw BackupRestoreException(RestoreFailure.DATABASE_UNUSABLE, "the archive's database failed a check", e)
        }
        migrate(file, phoneKey)
        return version
    }

    private fun checkAndRekey(file: File, archiveKey: ByteArray, phoneKey: ByteArray): Int {
        // The connection keeps the new key until it closes; the copy is wiped only after that.
        val newKey = phoneKey.copyOf()
        try {
            return open(file, archiveKey).use { database -> checkAndRekey(database, newKey) }
        } finally {
            newKey.fill(0)
        }
    }

    private fun open(file: File, key: ByteArray): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.path, key, null, SQLiteDatabase.OPEN_READWRITE, LOG_ONLY, null)

    private fun checkAndRekey(database: SQLiteDatabase, newKey: ByteArray): Int {
        val damagedPages = database.rawQuery("PRAGMA cipher_integrity_check", null).use { it.count }
        if (damagedPages > 0) unusable("$damagedPages page(s) fail their authentication")
        val quickCheck = database.compileStatement("PRAGMA quick_check").use { it.simpleQueryForString() }
        if (quickCheck != QUICK_CHECK_OK) unusable("the database fails its structural check")
        val version = database.compileStatement("PRAGMA user_version").use { it.simpleQueryForLong() }.toInt()
        if (version > AppDatabase.SCHEMA_VERSION) {
            throw BackupRestoreException(RestoreFailure.NEWER_VERSION, "schema $version is newer than this build")
        }
        if (version < 1) unusable("the database has no schema version")
        database.changePassword(newKey)
        return version
    }

    private fun migrate(file: File, phoneKey: ByteArray) {
        val passphrase = phoneKey.copyOf()
        val room = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .addMigrations(*ALL_MIGRATIONS.toTypedArray())
            // One file, no write-ahead log beside it, so the start moves a single file into place.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        try {
            room.openHelper.writableDatabase.version
        } catch (e: RuntimeException) {
            throw BackupRestoreException(RestoreFailure.DATABASE_UNUSABLE, "the archive's database did not migrate", e)
        } finally {
            room.close()
            passphrase.fill(0)
        }
    }

    private fun unusable(reason: String): Nothing =
        throw BackupRestoreException(RestoreFailure.DATABASE_UNUSABLE, reason)

    private companion object {
        const val NATIVE_LIBRARY = "sqlcipher"
        const val QUICK_CHECK_OK = "ok"

        /** Logs a reported corruption and does nothing else: no file is deleted on a restore's account. */
        val LOG_ONLY = DatabaseErrorHandler { _, e ->
            Timber.e(e, "SQLCipher reported a staged database corrupt; left as it is")
        }
    }
}
