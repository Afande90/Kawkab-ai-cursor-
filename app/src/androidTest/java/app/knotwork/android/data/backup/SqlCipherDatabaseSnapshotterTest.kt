package app.knotwork.android.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * [SqlCipherDatabaseSnapshotter] against real SQLCipher: the copy opens under the archive's key and no
 * other, holds every row and the schema version, is not a plain SQLite file, and the database it was
 * taken from — held open throughout, as Room holds it — stays in write-ahead logging and writable.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherDatabaseSnapshotterTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        dir = File(context.cacheDir, "snapshot-test-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun givenAnOpenDatabase_whenSnapshotted_thenTheCopyOpensOnlyUnderItsKeyWithEveryRowAndTheVersion() {
        // Given — a database in write-ahead logging, held open by another connection.
        val source = File(dir, "source.db")
        val database = SQLiteDatabase.openOrCreateDatabase(source, PHONE_KEY, null, null)
        database.enableWriteAheadLogging()
        database.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY, body TEXT NOT NULL)")
        repeat(ROWS) { database.execSQL("INSERT INTO notes(body) VALUES (?)", arrayOf<Any>("note $it")) }
        database.version = SCHEMA_VERSION
        val target = File(dir, "snapshot.db")

        try {
            // When
            val version = SqlCipherDatabaseSnapshotter({ source }, { PHONE_KEY.copyOf() }).snapshot(target, ARCHIVE_KEY)

            // Then — the source carries on as before.
            assertEquals(SCHEMA_VERSION, version)
            database.execSQL("INSERT INTO notes(body) VALUES ('after')")
            database.rawQuery("PRAGMA journal_mode", null).use {
                it.moveToFirst()
                assertEquals("wal", it.getString(0))
            }
        } finally {
            database.close()
        }

        // Then — the copy.
        val header = target.inputStream().use { it.readNBytes(SQLITE_MAGIC.size) }
        assertFalse("the copy is not a plain SQLite file", header.contentEquals(SQLITE_MAGIC))
        SQLiteDatabase.openDatabase(
            target.path,
            ARCHIVE_KEY,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null,
            null,
        ).use { copy ->
            assertEquals(SCHEMA_VERSION, copy.version)
            copy.rawQuery("SELECT COUNT(*), MIN(body), MAX(id) FROM notes", null).use {
                it.moveToFirst()
                assertEquals(ROWS, it.getInt(0))
                assertEquals("note 0", it.getString(1))
                assertEquals(ROWS, it.getInt(2))
            }
        }
        assertThrows(Exception::class.java) {
            SQLiteDatabase.openDatabase(
                target.path,
                PHONE_KEY,
                null,
                SQLiteDatabase.OPEN_READONLY,
                null,
                null,
            ).use { copy ->
                copy.rawQuery("SELECT COUNT(*) FROM notes", null).use { it.moveToFirst() }
            }
        }
    }

    private companion object {
        const val ROWS = 120
        const val SCHEMA_VERSION = 71
        val PHONE_KEY = ByteArray(32) { it.toByte() }
        val ARCHIVE_KEY = ByteArray(32) { (it * 7 + 3).toByte() }
        val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
