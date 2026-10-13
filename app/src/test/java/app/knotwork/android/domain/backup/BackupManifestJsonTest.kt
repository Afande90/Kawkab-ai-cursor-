package app.knotwork.android.domain.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [BackupManifestJson]: a manifest survives the round trip, unknown keys are ignored, optional fields are
 * omitted rather than written empty, and anything else is refused with its reason.
 */
class BackupManifestJsonTest {

    private val manifest = BackupManifest(
        createdAtEpochMillis = 1_791_849_600_000L,
        schemaVersion = 71,
        secretsIncluded = true,
        counts = BackupCounts(chats = 12, memories = 340, pipelines = 9, workspaceFiles = 4, attachments = 2),
        scheduledTasks = 1,
        models = listOf(
            BackupModelEntry("gemma.litertlm", 2_588_147_712L, sha256 = "ab".repeat(32), sourceUrl = "https://h/g"),
            BackupModelEntry("found-on-disk.litertlm", 10L, sha256 = null, sourceUrl = null),
        ),
        skippedFiles = 3,
    )

    @Test
    fun `given a manifest when encoded and decoded then it is unchanged`() {
        // Then
        assertEquals(manifest, BackupManifestJson.decode(BackupManifestJson.encode(manifest)))
    }

    @Test
    fun `given a model with no hash or source when encoded then those keys are left out`() {
        // When
        val model = JSONObject(BackupManifestJson.encode(manifest)).getJSONArray("models").getJSONObject(1)

        // Then
        assertFalse(model.has("sha256"))
        assertFalse(model.has("sourceUrl"))
        assertNull(BackupManifestJson.decode(BackupManifestJson.encode(manifest)).models[1].sourceUrl)
    }

    @Test
    fun `given keys a later writer added when decoded then they are ignored`() {
        // Given
        val json = JSONObject(BackupManifestJson.encode(manifest)).put("somethingNew", 42).toString()

        // Then
        assertEquals(manifest, BackupManifestJson.decode(json))
    }

    @Test
    fun `given a manifest without the skipped count when decoded then none were skipped`() {
        // Given
        val json = JSONObject(BackupManifestJson.encode(manifest)).apply { remove("skippedFiles") }.toString()

        // Then
        assertEquals(0, BackupManifestJson.decode(json).skippedFiles)
    }

    @Test
    fun `given a newer manifest version or text that is not a manifest when decoded then each is refused`() {
        // Given
        val newer = JSONObject(BackupManifestJson.encode(manifest)).put("manifestVersion", 2).toString()
        val broken =
            listOf(
                "",
                "[]",
                "{}",
                JSONObject(BackupManifestJson.encode(manifest)).apply {
                    remove("counts")
                }.toString(),
            )

        // Then
        assertEquals(
            BackupReadFailure.UNSUPPORTED_FORMAT_VERSION,
            assertThrows(BackupFormatException::class.java) { BackupManifestJson.decode(newer) }.failure,
        )
        broken.forEach { text ->
            assertEquals(
                text,
                BackupReadFailure.DAMAGED,
                assertThrows(BackupFormatException::class.java) { BackupManifestJson.decode(text) }.failure,
            )
        }
    }
}
