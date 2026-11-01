package app.kawkab.android.domain.voice

import app.kawkab.android.domain.voice.VoiceAnswers.Answer
import app.kawkab.android.domain.voice.VoiceAnswers.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 29 and spec §0.4.3: what a spoken answer may do. */
class VoiceAnswersTest {
    @Test
    fun `a spoken yes approves only with the setting on, the screen on and the phone unlocked`() {
        assertEquals(
            Decision.APPROVE,
            VoiceAnswers.decide(Answer.YES, voiceConfirmOn = true, screenOn = true, locked = false),
        )
        assertEquals(
            Decision.NEED_TAP,
            VoiceAnswers.decide(Answer.YES, voiceConfirmOn = false, screenOn = true, locked = false),
        )
        assertEquals(
            Decision.NEED_TAP,
            VoiceAnswers.decide(Answer.YES, voiceConfirmOn = true, screenOn = true, locked = true),
        )
        assertEquals(
            Decision.NEED_TAP,
            VoiceAnswers.decide(Answer.YES, voiceConfirmOn = true, screenOn = false, locked = false),
        )
    }

    @Test
    fun `a spoken no always cancels and anything else waits for a tap`() {
        assertEquals(
            Decision.DENY,
            VoiceAnswers.decide(Answer.NO, voiceConfirmOn = false, screenOn = false, locked = true),
        )
        assertEquals(
            Decision.NEED_TAP,
            VoiceAnswers.decide(Answer.UNCLEAR, voiceConfirmOn = true, screenOn = true, locked = false),
        )
    }

    @Test
    fun `answers are judged on the whole utterance in English and Arabic`() {
        assertEquals(Answer.YES, VoiceAnswers.answer("Yes."))
        assertEquals(Answer.YES, VoiceAnswers.answer("Kawkab, yes please"))
        assertEquals(Answer.YES, VoiceAnswers.answer("أيوه"))
        assertEquals(Answer.NO, VoiceAnswers.answer("لا"))
        assertEquals(Answer.NO, VoiceAnswers.answer("No!"))
        assertEquals(Answer.UNCLEAR, VoiceAnswers.answer("yes but call Omar instead"))
        assertEquals(Answer.UNCLEAR, VoiceAnswers.answer(""))
    }

    @Test
    fun `undo words are recognised alone`() {
        assertTrue(VoiceAnswers.isUndo("Undo"))
        assertTrue(VoiceAnswers.isUndo("cancel that."))
        assertTrue(VoiceAnswers.isUndo("كوكب تراجع"))
        assertTrue(VoiceAnswers.isUndo("الغيها"))
        assertFalse(VoiceAnswers.isUndo("undo my last email to Omar"))
        assertFalse(VoiceAnswers.isUndo("set an alarm"))
    }
}
