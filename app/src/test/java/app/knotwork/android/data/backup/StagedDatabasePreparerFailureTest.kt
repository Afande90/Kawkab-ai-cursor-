package app.knotwork.android.data.backup

import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import app.knotwork.android.domain.backup.RestoreFailure
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How [SqlCipherStagedDatabasePreparer] names a failure of the staged database's work: a full disk anywhere
 * in the chain is the phone being full, never a damaged archive; anything else is a database that cannot be
 * used. The work itself runs on SQLCipher and is tested on a device.
 */
class StagedDatabasePreparerFailureTest {

    @Test
    fun `given SQLite ran out of space anywhere in the chain when named then the phone is full`() {
        // Given — as Room wraps what SQLite throws.
        val wrapped = IllegalStateException("migration failed", SQLiteFullException("database or disk is full"))

        // Then
        assertEquals(RestoreFailure.NO_SPACE, SqlCipherStagedDatabasePreparer.failureOf(wrapped, "x").failure)
        assertEquals(
            RestoreFailure.NO_SPACE,
            SqlCipherStagedDatabasePreparer.failureOf(SQLiteFullException("full"), "x").failure,
        )
    }

    @Test
    fun `given any other failure when named then the database cannot be used`() {
        assertEquals(
            RestoreFailure.DATABASE_UNUSABLE,
            SqlCipherStagedDatabasePreparer.failureOf(SQLiteException("file is not a database"), "x").failure,
        )
    }
}
