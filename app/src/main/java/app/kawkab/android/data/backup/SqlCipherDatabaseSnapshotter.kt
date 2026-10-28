package app.kawkab.android.data.backup

import android.content.Context
import android.database.sqlite.SQLiteFullException
import android.net.Uri
import app.kawkab.android.data.local.AppDatabase
import app.kawkab.android.data.local.EncryptedDbPassphraseProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a consistent copy of the app's database for a backup.
 *
 * @see SqlCipherDatabaseSnapshotter
 */
internal interface DatabaseSnapshotter {

    /**
     * Copies the database into [target], encrypted under [key].
     *
     * @param target Where the copy goes; replaced if it exists.
     * @param key The 32 bytes the copy is encrypted with — the archive's own, never a phone's key.
     * @return The schema version of the copy.
     * @throws SQLiteFullException When the phone has no room for the copy.
     */
    fun snapshot(target: File, key: ByteArray): Int
}

/**
 * [DatabaseSnapshotter] on SQLCipher's `sqlcipher_export()`.
 *
 * SQLCipher refuses SQLite's backup API for an encrypted database; `sqlcipher_export(target, source)`
 * copies the schema and every row of one database into another, but not `user_version`, which Room reads
 * as the schema version — so that is set on the copy afterwards.
 *
 * The copy is opened as the main database of a connection of its own, keyed with the archive's key, and
 * the app's database is attached to it **read-only**, under this phone's passphrase. A new file has no
 * write-ahead log, so its pool holds that one connection and every statement sees the attachment; Room's
 * connections are never touched. `BEGIN IMMEDIATE` then takes the write lock on the copy and, the app's
 * database being read-only here, only a read transaction on it: one snapshot of the file. In write-ahead
 * logging Room's writers carry on; on a rollback journal (a low-RAM phone) a writer's commit waits for the
 * export as for any reader, and fails if the wait outlasts SQLite's busy timeout.
 *
 * Every statement runs through a call that reports a failed step. `rawExecSQL` does not — sqlcipher-android
 * steps the statement and drops the result — so a copy cut short would read as whole
 * (`RawExecSqlGuardTest`).
 *
 * Before anything is opened, the copy's directory must have room for the app's database and its
 * write-ahead log — about what a copy takes. That is how a full phone is told apart: a disk filling
 * during the export reaches the caller only as a failed function, which does not say why.
 *
 * The copy is encrypted under the archive's own random key, so this phone's database key never leaves
 * it, and nothing unencrypted is written to disk. A reported corruption is only logged: the stock
 * handler deletes nothing solely because this build has a codec, and would otherwise delete the file of
 * every attached database — the app's own among them.
 *
 * @param databaseFile Where the app's database is.
 * @param passphrase Gives this phone's database passphrase; a fresh copy each call, wiped here after use.
 * @param usableBytes How many bytes can still be written in a directory.
 */
@Singleton
internal class SqlCipherDatabaseSnapshotter(
    private val databaseFile: () -> File,
    private val passphrase: () -> ByteArray,
    private val usableBytes: (File) -> Long = File::getUsableSpace,
) : DatabaseSnapshotter {

    /** Snapshots the app's own database. */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        passphraseProvider: EncryptedDbPassphraseProvider,
    ) : this(
        databaseFile = { context.getDatabasePath(AppDatabase.DATABASE_NAME) },
        passphrase = { passphraseProvider.getOrCreatePassphrase() },
    )

    override fun snapshot(target: File, key: ByteArray): Int {
        require(key.size == KEY_LENGTH) { "the snapshot key must be $KEY_LENGTH bytes" }
        val source = databaseFile()
        val needed = File(source.path + WAL_SUFFIX).length() + source.length()
        if (usableBytes(target.absoluteFile.parentFile ?: target.absoluteFile) < needed) {
            throw SQLiteFullException("no room on the phone for the database snapshot")
        }
        System.loadLibrary(NATIVE_LIBRARY)
        target.delete()
        return SQLiteDatabase.openOrCreateDatabase(target, key, null, LOG_ONLY).use { copy ->
            attachReadOnly(copy, source)
            export(copy)
        }
    }

    private fun attachReadOnly(copy: SQLiteDatabase, source: File) {
        val uri = Uri.fromFile(source.absoluteFile).buildUpon()
            .appendQueryParameter(MODE_PARAMETER, READ_ONLY_MODE).build().toString()
        val secret = passphrase()
        try {
            copy.execSQL("ATTACH DATABASE ? AS $SOURCE KEY ?", arrayOf<Any>(uri, secret))
        } finally {
            // SQLite holds its own copy of an attached database's key.
            secret.fill(0)
        }
    }

    private fun export(copy: SQLiteDatabase): Int {
        copy.beginTransactionNonExclusive()
        val version = try {
            copy.compileStatement("SELECT sqlcipher_export('main', '$SOURCE')").use { it.simpleQueryForString() }
            copy.compileStatement("PRAGMA $SOURCE.user_version").use { it.simpleQueryForLong() }.toInt().also {
                copy.version = it
                copy.setTransactionSuccessful()
            }
        } catch (e: RuntimeException) {
            // The first error is the story; a failed rollback rides along with it.
            try {
                copy.endTransaction()
            } catch (rollback: RuntimeException) {
                e.addSuppressed(rollback)
            }
            throw e
        }
        copy.endTransaction()
        return version
    }

    private companion object {
        const val NATIVE_LIBRARY = "sqlcipher"

        /** The length of the snapshot key: an empty or missing key would leave the copy unencrypted. */
        const val KEY_LENGTH = 32

        /** The name the app's database is attached under for the length of the export. */
        const val SOURCE = "app_database"

        /** The app's database is attached through a `file:` URI, which is how SQLite is told to open it read-only. */
        const val MODE_PARAMETER = "mode"
        const val READ_ONLY_MODE = "ro"

        /** The write-ahead log beside the app's database, which holds committed rows not yet in the file. */
        const val WAL_SUFFIX = "-wal"

        /** Logs a reported corruption and does nothing else: no file is deleted on a backup's account. */
        val LOG_ONLY = DatabaseErrorHandler { _, e ->
            Timber.e(e, "SQLCipher reported a database corrupt while taking a backup snapshot; left as it is")
        }
    }
}
