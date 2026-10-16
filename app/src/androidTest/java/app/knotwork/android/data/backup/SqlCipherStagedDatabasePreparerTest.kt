package app.knotwork.android.data.backup

import android.content.ContentValues
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.knotwork.android.data.local.AppDatabase
import app.knotwork.android.domain.backup.BackupRestoreException
import app.knotwork.android.domain.backup.RestoreFailure
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * [SqlCipherStagedDatabasePreparer] against real SQLCipher and Room: a database copy from an archive comes out
 * keyed for this phone and at this build's schema — an older one migrated, the rows kept — and one of a
 * later schema, one that is not a database, and one under another key are refused.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherStagedDatabasePreparerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        SupportOpenHelperFactory(ARCHIVE_KEY.copyOf()),
    )

    private lateinit var copy: File

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        copy = context.getDatabasePath(TEST_DB)
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun givenACopyAtThisSchema_whenPrepared_thenItOpensUnderThisPhonesKeyWithItsRows() {
        // Given
        helper.createDatabase(TEST_DB, AppDatabase.SCHEMA_VERSION).use { db ->
            db.insertModel(includeSource = true)
        }

        // When
        val version = preparer().prepare(copy, ARCHIVE_KEY.copyOf(), PHONE_KEY.copyOf())

        // Then
        assertEquals(AppDatabase.SCHEMA_VERSION, version)
        assertEquals("https://hub/gemma" to "gemma.litertlm", readModel())
        assertThrows(Exception::class.java) { openWith(ARCHIVE_KEY).use { it.version } }
    }

    @Test
    fun givenACopyAtAnOlderSchema_whenPrepared_thenItIsMigratedAndKeepsItsRows() {
        // Given — the schema before the model registry recorded where a model came from.
        helper.createDatabase(TEST_DB, OLDER_SCHEMA).use { db ->
            db.insertModel(includeSource = false)
        }

        // When
        val version = preparer().prepare(copy, ARCHIVE_KEY.copyOf(), PHONE_KEY.copyOf())

        // Then
        assertEquals(OLDER_SCHEMA, version)
        openWith(PHONE_KEY).use { assertEquals(AppDatabase.SCHEMA_VERSION, it.version) }
        assertEquals(null to "gemma.litertlm", readModel())
    }

    @Test
    fun givenACopyOfALaterSchema_whenPrepared_thenItIsRefusedAsNewer() {
        // Given
        SQLiteDatabase.openOrCreateDatabase(copy, ARCHIVE_KEY, null, null).use { db ->
            db.execSQL("CREATE TABLE later(id INTEGER PRIMARY KEY)")
            db.version = AppDatabase.SCHEMA_VERSION + 1
        }

        // When
        val failure = assertThrows(BackupRestoreException::class.java) {
            preparer().prepare(copy, ARCHIVE_KEY.copyOf(), PHONE_KEY.copyOf())
        }

        // Then
        assertEquals(RestoreFailure.NEWER_VERSION, failure.failure)
    }

    @Test
    fun givenBytesThatAreNotADatabase_orAnotherKey_whenPrepared_thenTheDatabaseIsUnusable() {
        // Given — random bytes.
        copy.parentFile?.mkdirs()
        copy.writeBytes(Random(3).nextBytes(8192))

        // Then
        assertUnusable(ARCHIVE_KEY)

        // Given — a real copy under another key.
        context.deleteDatabase(TEST_DB)
        helper.createDatabase(TEST_DB, AppDatabase.SCHEMA_VERSION).close()

        // Then
        assertUnusable(PHONE_KEY)
    }

    private fun assertUnusable(key: ByteArray) {
        val failure = assertThrows(BackupRestoreException::class.java) {
            preparer().prepare(copy, key.copyOf(), PHONE_KEY.copyOf())
        }
        assertEquals(RestoreFailure.DATABASE_UNUSABLE, failure.failure)
    }

    private fun preparer() = SqlCipherStagedDatabasePreparer(context)

    private fun SupportSQLiteDatabase.insertModel(includeSource: Boolean) {
        val values = ContentValues().apply {
            put("name", "gemma.litertlm")
            put("path", "/old/phone/gemma.litertlm")
            put("size", 1_000L)
            put("isActive", 1)
            put("supportsVision", 0)
            put("supportsAudio", 0)
            if (includeSource) put("sourceUrl", "https://hub/gemma")
        }
        insert("local_models", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, values)
    }

    /** The one model row, as (source, name), read through Room under this phone's key. */
    private fun readModel(): Pair<String?, String> {
        val room = Room.databaseBuilder(context, AppDatabase::class.java, copy.absolutePath)
            .openHelperFactory(SupportOpenHelperFactory(PHONE_KEY.copyOf()))
            .build()
        try {
            room.openHelper.readableDatabase.query("SELECT sourceUrl, name FROM local_models").use {
                it.moveToFirst()
                return (if (it.isNull(0)) null else it.getString(0)) to it.getString(1)
            }
        } finally {
            room.close()
        }
    }

    private fun openWith(key: ByteArray): SQLiteDatabase =
        SQLiteDatabase.openDatabase(copy.path, key, null, SQLiteDatabase.OPEN_READONLY, null, null)

    private companion object {
        const val TEST_DB = "staged-restore-test.db"
        const val OLDER_SCHEMA = 70
        val ARCHIVE_KEY = ByteArray(32) { (it * 3 + 1).toByte() }
        val PHONE_KEY = ByteArray(32) { (it * 5 + 2).toByte() }
    }
}
