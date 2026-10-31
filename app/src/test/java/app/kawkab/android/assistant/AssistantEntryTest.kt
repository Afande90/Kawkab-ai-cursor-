package app.kawkab.android.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Task 23k: the assistant is declared correctly, and two fallbacks always reach Kawkab. */
class AssistantEntryTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun `the voice interaction service and its session service are declared`() {
        val config = File("src/main/res/xml/voice_interaction_service.xml").readText()
        assertTrue(
            config.contains("android:sessionService=\"app.kawkab.android.assistant.KawkabAssistantSessionService\""),
        )
        assertTrue(config.contains("android:supportsAssist=\"true\""))
        assertTrue(manifest.contains("android.service.voice.VoiceInteractionService"))
        assertTrue(manifest.contains(".assistant.KawkabAssistantSessionService"))
    }

    @Test
    fun `an Ask Kawkab tile and launcher shortcut exist`() {
        assertTrue(manifest.contains(".assistant.AskKawkabTileService"))
        assertTrue(manifest.contains("android.service.quicksettings.action.QS_TILE"))
        val shortcuts = File("src/main/res/xml/shortcuts.xml").readText()
        assertTrue(shortcuts.contains("android:shortcutId=\"ask_kawkab\""))
        assertTrue(shortcuts.contains(AssistantEntry.NEW_CHAT_URI))
    }

    @Test
    fun `without the assistant role the panel is not shown and the caller falls back`() {
        assertFalse(KawkabVoiceInteractionService.showPanel())
    }

    @Test
    fun `Phone access names the power-button setting in both languages`() {
        listOf("values", "values-ar").forEach { dir ->
            val strings = File("src/main/res/$dir/strings_phone_access.xml").readText()
            assertTrue(dir, strings.contains("name=\"phone_access_power_button\""))
            assertTrue(dir, strings.contains("name=\"phone_access_assistant_role_off\""))
        }
    }
}
