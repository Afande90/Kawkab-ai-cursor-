package app.knotwork.android.data.backup

import android.content.Context
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.data.local.EncryptedDbPassphraseProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SQLiteDatabase
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
     */
    fun snapshot(target: File, key: ByteArray): Int
}

/**
 * [DatabaseSnapshotter] on SQLCipher's `sqlcipher_export()`.
 *
 * SQLCipher refuses SQLite's backup API for an encrypted database; `sqlcipher_export` copies the schema
 * and every row into an attached database under its own key, but not `user_version`, which Room reads
 * as the schema version — so that is set on the copy afterwards.
 *
 * The copy runs on a connection of its own to the database file, opened with this phone's passphrase,
 * never on Room's: attaching through the framework's `execSQL` would switch Room's database out of
 * write-ahead logging for the rest of the process, so every statement here goes through `rawExecSQL`
 * on one thread. Inside one deferred transaction, the export reads one snapshot of the file while
 * Room's writers carry on in the write-ahead log.
 *
 * The copy is encrypted under the archive's own random key, so this phone's database key never leaves
 * it, and nothing unencrypted is written to disk.
 *
 * @param databaseFile Where the app's database is.
 * @param passphrase Gives this phone's database passphrase; a fresh copy each call, wiped here after use.
 */
@Singleton
internal class SqlCipherDatabaseSnapshotter(
    private val databaseFile: () -> File,
    private val passphrase: () -> ByteArray,
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
        System.loadLibrary(NATIVE_LIBRARY)
        target.delete()
        val secret = passphrase()
        try {
            val database = SQLiteDatabase.openDatabase(
                databaseFile().path,
                secret,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
                null,
                null,
            )
            return database.use { export(it, target, key) }
        } finally {
            // The connection keeps the array until it closes; only then is it safe to wipe.
            secret.fill(0)
        }
    }

    private fun export(database: SQLiteDatabase, target: File, key: ByteArray): Int {
        val version = database.version
        database.rawExecSQL("ATTACH DATABASE ? AS $ALIAS KEY ?", target.path, key)
        try {
            database.rawExecSQL("BEGIN DEFERRED")
            try {
                database.rawExecSQL("SELECT sqlcipher_export('$ALIAS')")
                database.rawExecSQL("COMMIT")
            } catch (e: RuntimeException) {
                database.rawExecSQL("ROLLBACK")
                throw e
            }
            database.rawExecSQL("PRAGMA $ALIAS.user_version = $version")
        } finally {
            database.rawExecSQL("DETACH DATABASE $ALIAS")
        }
        return version
    }

    private companion object {
        const val NATIVE_LIBRARY = "sqlcipher"

        /** The name the copy is attached under for the length of the export. */
        const val ALIAS = "backup_snapshot"
    }
}
