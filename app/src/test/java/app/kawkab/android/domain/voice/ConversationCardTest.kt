package app.kawkab.android.domain.voice

import app.kawkab.android.domain.voice.ConversationLoop.Phase
import app.kawkab.android.domain.voice.ConversationLoop.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 29: a card is read aloud, then waits for a tap or listens once for an answer. */
class ConversationCardTest {
    private fun waitingLoop(): ConversationLoop = ConversationLoop().apply {
        tap()
        heard("call Abdalla Mubadala")
    }

    @Test
    fun `stop is accepted while the announcement is still speaking`() {
        val loop = waitingLoop()
        loop.waitsForTap("Calling Abdalla Mubadala", voiceMayAnswer = true)
        assertEquals(Step.Answer("stop"), loop.heard("stop"))
        assertEquals(Phase.WAITING, loop.phase)
    }

    @Test
    fun `the card is read aloud, then only a tap confirms, and its result is still spoken`() {
        val loop = waitingLoop()
        val read = loop.waitsForTap("Call Abdalla Mubadala?", voiceMayAnswer = false)
        assertTrue(read is Step.Speak)
        assertEquals("Call Abdalla Mubadala?", (read as Step.Speak).text)
        assertEquals(Step.Stop, loop.spoken())
        assertEquals(Phase.WAITING, loop.phase)
        assertTrue(loop.replied("Calling Abdalla Mubadala.") is Step.Speak)
    }

    @Test
    fun `with voice answers allowed it listens once and hands the answer over`() {
        val loop = waitingLoop()
        loop.waitsForTap("Call Abdalla Mubadala?", voiceMayAnswer = true)
        assertEquals(Step.Listen, loop.spoken())
        assertEquals(Step.Answer("yes"), loop.heard("yes"))
        assertEquals(Phase.WAITING, loop.phase)
    }

    @Test
    fun `silence after the card leaves it for a tap`() {
        val loop = waitingLoop()
        loop.waitsForTap("Call Abdalla Mubadala?", voiceMayAnswer = true)
        loop.spoken()
        assertEquals(Step.Stop, loop.silence())
        assertEquals(Phase.WAITING, loop.phase)
    }

    @Test
    fun `without a sentence it stops listening as before`() {
        val loop = waitingLoop()
        assertEquals(Step.Stop, loop.waitsForTap())
        assertEquals(Phase.IDLE, loop.phase)
    }
}
