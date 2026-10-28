package app.kawkab.android.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BackupKdfParams.isAcceptable]: the writer's parameters and every bound are accepted, one step past
 * any bound is refused.
 */
class BackupKdfParamsTest {

    @Test
    fun `given the writer's parameters when checked then a reader accepts them`() {
        // Then
        assertTrue(BackupKdfParams.DEFAULT.isAcceptable())
    }

    @Test
    fun `given parameters at every bound when checked then they are accepted`() {
        // Given
        val atBounds = listOf(
            BackupKdfParams(memoryKiB = BackupKdfParams.MAX_MEMORY_KIB, iterations = 1, parallelism = 1),
            BackupKdfParams(memoryKiB = 8, iterations = BackupKdfParams.MAX_ITERATIONS, parallelism = 1),
            BackupKdfParams(memoryKiB = 8 * 4, iterations = 1, parallelism = BackupKdfParams.MAX_PARALLELISM),
        )

        // Then
        atBounds.forEach { assertTrue(it.toString(), it.isAcceptable()) }
    }

    @Test
    fun `given parameters one step past a bound when checked then each is refused`() {
        // Given
        val pastBounds = listOf(
            BackupKdfParams(memoryKiB = BackupKdfParams.MAX_MEMORY_KIB + 1, iterations = 3, parallelism = 1),
            BackupKdfParams(memoryKiB = 7, iterations = 3, parallelism = 1),
            BackupKdfParams(memoryKiB = 8 * 2 - 1, iterations = 3, parallelism = 2),
            BackupKdfParams(memoryKiB = 65_536, iterations = 0, parallelism = 1),
            BackupKdfParams(memoryKiB = 65_536, iterations = BackupKdfParams.MAX_ITERATIONS + 1, parallelism = 1),
            BackupKdfParams(memoryKiB = 65_536, iterations = 3, parallelism = 0),
            BackupKdfParams(memoryKiB = 65_536, iterations = 3, parallelism = BackupKdfParams.MAX_PARALLELISM + 1),
            BackupKdfParams(memoryKiB = Int.MIN_VALUE, iterations = 3, parallelism = 1),
        )

        // Then
        pastBounds.forEach { assertFalse(it.toString(), it.isAcceptable()) }
    }
}
