package app.kawkab.android.architecture

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * No production code runs a statement through SQLCipher's `rawExecSQL`.
 *
 * sqlcipher-android 4.19 steps a `rawExecSQL` statement and drops the result code: a statement that fails
 * when stepped — a full disk, a lock, a constraint — returns as if it had run. The backup's database
 * snapshot once ran its `ATTACH` and export that way: on a device both failed, and only the next
 * statement's compile error reached the caller; a failure later in the export would have left a partial
 * copy reported as whole (`SqlCipherDatabaseSnapshotterTest` on a device pins the library's behaviour).
 * `execSQL`, `compileStatement(…).execute()` / `simpleQueryFor…()` and the transaction calls all report a
 * failed step.
 */
class RawExecSqlGuardTest {

    @Test
    fun `given the production sources when read then none runs a statement through rawExecSQL`() {
        // Given
        val callers = ProductionSources.code.filterValues { code -> RAW_EXEC.containsMatchIn(code) }.keys

        // Then
        assertEquals("statements run through rawExecSQL, whose failures are dropped", emptySet<String>(), callers)
    }

    private companion object {
        val RAW_EXEC = Regex("""\brawExecSQL\b""")
    }
}
