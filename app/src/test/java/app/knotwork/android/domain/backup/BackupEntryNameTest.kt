package app.knotwork.android.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BackupEntryName]: relative file paths in any script pass; every name that could leave the directory
 * it is unpacked into, or hide a character, is refused.
 */
class BackupEntryNameTest {

    @Test
    fun `given relative file paths when checked then they are valid`() {
        // Given
        val names = listOf(
            "manifest.json",
            "database/agent_database.db",
            "files/agent_workspace/notes/día de campo.md",
            "files/agent_workspace/заметки/список.txt",
            "a".repeat(BackupEntryName.MAX_LENGTH),
            "files/.hidden",
            "files/..notes",
        )

        // Then
        names.forEach { assertTrue(it, BackupEntryName.isValid(it)) }
    }

    @Test
    fun `given names that could leave the target directory or hide a character when checked then each is refused`() {
        // Given
        val names = listOf(
            "",
            "/etc/passwd",
            "../outside",
            "files/../../outside",
            "files/./notes",
            "files//notes",
            "files/notes/",
            "files\\notes",
            "files/no\u0000te",
            "files/no\nte",
            "files/no\u007fte",
            "a".repeat(BackupEntryName.MAX_LENGTH + 1),
            ".",
            "..",
        )

        // Then
        names.forEach { assertFalse(it, BackupEntryName.isValid(it)) }
    }
}
