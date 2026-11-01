package app.kawkab.android.domain.voice

import app.kawkab.android.assistant.VoiceStart
import app.kawkab.android.domain.text.ReplyLanguage
import app.kawkab.android.domain.voice.ConversationLoop.Phase
import app.kawkab.android.domain.voice.ConversationLoop.Step
import app.kawkab.android.voice.OfflineSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Task 23l: listen, send, speak, listen again; tap stops; speaking over the reply stops it. */
class ConversationLoopTest {

    @Test
    fun `one full turn and back to listening`() {
        val loop = ConversationLoop()
        assertEquals(Step.Listen, loop.tap())
        assertEquals(Step.Send("set an alarm for 6"), loop.heard(" set an alarm for 6 "))
        assertEquals(Phase.WAITING, loop.phase)
        assertEquals(
            Step.Speak("Alarm set for 6:00 AM.", ReplyLanguage.ENGLISH),
            loop.replied("Alarm set for 6:00 AM."),
        )
        assertEquals(Step.Listen, loop.spoken())
        assertEquals(Phase.LISTENING, loop.phase)
    }

    @Test
    fun `an Arabic reply is spoken in Arabic`() {
        val loop = ConversationLoop()
        loop.tap()
        loop.heard("يا زول الساعة كم")
        val step = loop.replied("الساعة تسعة هسي") as Step.Speak
        assertEquals(ReplyLanguage.SUDANESE, step.language)
    }

    @Test
    fun `speaking over the reply stops the speech`() {
        val loop = ConversationLoop()
        loop.tap()
        loop.heard("hello")
        loop.replied("A long answer")
        assertEquals(Step.StopSpeakingAndListen, loop.bargeIn())
        assertEquals(Phase.LISTENING, loop.phase)
    }

    @Test
    fun `a second tap stops, and silence twice in a row ends the loop`() {
        val loop = ConversationLoop()
        loop.tap()
        assertEquals(Step.Stop, loop.tap())
        assertEquals(Phase.IDLE, loop.phase)

        loop.tap()
        assertEquals(Step.Listen, loop.silence())
        assertEquals(Step.Listen, loop.silence())
        assertEquals(Step.Stop, loop.silence())
        assertEquals(Phase.IDLE, loop.phase)
    }

    @Test
    fun `a tap card stops the loop, so voice never confirms it`() {
        val loop = ConversationLoop()
        loop.tap()
        loop.heard("call Ahmad")
        assertEquals(Step.Stop, loop.waitsForTap())
        assertEquals(Step.None, loop.heard("yes"))
        assertEquals(Phase.IDLE, loop.phase)
    }

    @Test
    fun `only a long-press of power starts listening in the panel`() {
        assertTrue(VoiceStart.fromGesture(android.service.voice.VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE))
        assertFalse(VoiceStart.fromGesture(android.service.voice.VoiceInteractionSession.SHOW_WITH_ASSIST))
    }

    @Test
    fun `the recognizer listens in Sudanese Arabic on an Arabic phone and English otherwise`() {
        assertEquals("ar-SD", OfflineSpeech.languageFor(Locale.forLanguageTag("ar")))
        assertEquals("en-US", OfflineSpeech.languageFor(Locale.US))
        assertEquals(listOf("ar-SD", "ar", "en-US"), OfflineSpeech.LANGUAGES)
    }
}
