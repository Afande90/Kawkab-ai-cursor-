package app.kawkab.android.data.backup

import android.database.sqlite.SQLiteFullException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * What [SqlCipherDatabaseSnapshotter] decides before it opens anything: it refuses a key that is not 32
 * bytes — SQLCipher attaches a database with an empty key unencrypted — and reports a phone without room
 * for the copy as full. The copy itself is tested on a device.
 */
class SqlCipherDatabaseSnapshotterTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `given a key that is not 32 bytes when snapshotted then it refuses before touching the database`() {
        // Given
        val snapshotter = SqlCipherDatabaseSnapshotter(
            databaseFile = { throw AssertionError("the database was reached") },
            passphrase = { throw AssertionError("the passphrase was read") },
        )

        // Then
        listOf(ByteArray(0), ByteArray(16), ByteArray(33)).forEach { key ->
            assertThrows(IllegalArgumentException::class.java) { snapshotter.snapshot(File("unused"), key) }
        }
    }

    @Test
    fun `given less room than the database and its log when snapshotted then the phone is full`() {
        // Given
        val (source, target) = databaseOf(fileBytes = 4096, logBytes = 1024)
        val asked = mutableListOf<File>()
        val snapshotter = SqlCipherDatabaseSnapshotter(
            databaseFile = { source },
            passphrase = { throw AssertionError("the passphrase was read") },
            usableBytes = { dir ->
                asked += dir
                4096L + 1024 - 1
            },
        )

        // Then
        assertThrows(SQLiteFullException::class.java) { snapshotter.snapshot(target, ByteArray(32)) }
        assertEquals(listOf(target.parentFile), asked)
    }

    @Test
    fun `given exactly the room the database and its log take when snapshotted then it goes on to open SQLCipher`() {
        // Given
        val (source, target) = databaseOf(fileBytes = 4096, logBytes = 1024)
        val snapshotter = SqlCipherDatabaseSnapshotter(
            databaseFile = { source },
            passphrase = { throw AssertionError("the passphrase was read") },
            usableBytes = { 4096L + 1024 },
        )

        // Then — past the check: the JVM has no SQLCipher library to load.
        assertThrows(UnsatisfiedLinkError::class.java) { snapshotter.snapshot(target, ByteArray(32)) }
    }

    private fun databaseOf(fileBytes: Int, logBytes: Int): Pair<File, File> {
        val dir = folder.newFolder()
        val source = File(dir, "agent.db").apply { writeBytes(ByteArray(fileBytes)) }
        File(dir, "agent.db-wal").writeBytes(ByteArray(logBytes))
        return source to File(folder.newFolder(), "snapshot.db")
    }
}
