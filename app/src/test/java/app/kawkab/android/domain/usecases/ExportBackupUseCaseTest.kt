package app.kawkab.android.domain.usecases

import app.kawkab.android.domain.backup.BackupCounts
import app.kawkab.android.domain.backup.BackupExporter
import app.kawkab.android.domain.backup.BackupManifest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * [ExportBackupUseCase]: a password of at least twelve characters, counted as code points, reaches the
 * exporter; a shorter one is refused before anything is written, and the stream is closed either way.
 */
class ExportBackupUseCaseTest {

    private val manifest = BackupManifest(0L, 71, false, BackupCounts(0, 0, 0, 0, 0), 0, emptyList())
    private val exporter = mockk<BackupExporter>()
    private val useCase = ExportBackupUseCase(exporter)

    @Test
    fun `given a password of twelve characters when exported then the exporter writes the archive`() = runTest {
        // Given
        val target = ByteArrayOutputStream()
        coEvery { exporter.export(target, any(), true, any()) } returns manifest

        // When
        val result = useCase(target, "twelve chars".toCharArray(), includeSecrets = true) { }

        // Then
        assertEquals(manifest, result)
        coVerify(exactly = 1) { exporter.export(target, any(), true, any()) }
    }

    @Test
    fun `given eleven characters, or six emoji in twelve UTF-16 units, when exported then it is refused`() {
        // Given — "\uD83D\uDD12" is one emoji, two UTF-16 units.
        val short = "eleven char".toCharArray()
        val emoji = "\uD83D\uDD12".repeat(6).toCharArray()

        // Then
        listOf(short, emoji).forEach { password ->
            val target = ClosingProbe()
            assertThrows(IllegalArgumentException::class.java) {
                runTest { useCase(target, password, includeSecrets = false) { } }
            }
            assertTrue("the stream is closed", target.closed)
        }
        coVerify(exactly = 0) { exporter.export(any(), any(), any(), any()) }
    }

    private class ClosingProbe : OutputStream() {
        var closed = false

        override fun write(b: Int) = Unit

        override fun close() {
            closed = true
        }
    }
}
