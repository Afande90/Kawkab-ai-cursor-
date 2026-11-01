package app.kawkab.android.presentation.ui.chat.home

import android.speech.SpeechRecognizer
import app.kawkab.android.voice.OfflineSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Task 23c: the mic uses the offline recognizer and explains an empty result. */
class DictationOutcomeTest {

    @Test
    fun `a missing language offers the download, silence asks again`() {
        assertEquals(
            DictationOutcome.LANGUAGE_MISSING,
            DictationOutcome.of(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE),
        )
        assertEquals(
            DictationOutcome.LANGUAGE_MISSING,
            DictationOutcome.of(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED),
        )
        assertEquals(DictationOutcome.NOTHING_HEARD, DictationOutcome.of(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(DictationOutcome.NOTHING_HEARD, DictationOutcome.of(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(DictationOutcome.FAILED, DictationOutcome.of(SpeechRecognizer.ERROR_NETWORK))
    }

    @Test
    fun `the mic dictates instead of asking the model to transcribe`() {
        val screen = File("src/main/java/app/kawkab/android/presentation/ui/chat/home/ChatHomeScreen.kt").readText()
        assertTrue(screen.contains("onMic = onMicDictate"))
        assertFalse(screen.contains("onMic = viewModel.voice::onMicClicked"))
        val speech = File("src/main/java/app/kawkab/android/voice/OfflineSpeech.kt").readText()
        assertTrue(speech.contains("EXTRA_PREFER_OFFLINE, true"))
        assertEquals(listOf("ar-SD", "ar", "en-US"), OfflineSpeech.LANGUAGES)
    }
}
