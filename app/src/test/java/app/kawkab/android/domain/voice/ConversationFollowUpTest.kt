package app.kawkab.android.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 28: after a spoken reply Kawkab listens for a follow-up; silence then ends the talk. */
class ConversationFollowUpTest {
    private fun spokenLoop(): ConversationLoop {
        val loop = ConversationLoop()
        loop.tap()
        loop.heard("set an alarm for six")
        loop.replied("Alarm set for 6:00.")
        return loop
    }

    @Test
    fun `after speaking it listens for a follow-up`() {
        val loop = spokenLoop()
        assertEquals(ConversationLoop.Step.Listen, loop.spoken())
        assertTrue(loop.followingUp)
    }

    @Test
    fun `silence after a reply ends the conversation at once`() {
        val loop = spokenLoop()
        loop.spoken()
        assertEquals(ConversationLoop.Step.Stop, loop.silence())
        assertEquals(ConversationLoop.Phase.IDLE, loop.phase)
        assertFalse(loop.followingUp)
    }

    @Test
    fun `a follow-up is sent without the wake word`() {
        val loop = spokenLoop()
        loop.spoken()
        assertEquals(ConversationLoop.Step.Send("and one for seven"), loop.heard("and one for seven"))
        assertFalse(loop.followingUp)
    }

    @Test
    fun `the first listen still forgives a silent round`() {
        val loop = ConversationLoop()
        loop.tap()
        assertEquals(ConversationLoop.Step.Listen, loop.silence())
    }

    @Test
    fun `talking over the reply is not a follow-up timeout`() {
        val loop = spokenLoop()
        assertEquals(ConversationLoop.Step.StopSpeakingAndListen, loop.bargeIn())
        assertFalse(loop.followingUp)
        assertEquals(ConversationLoop.Step.Listen, loop.silence())
    }

    @Test
    fun `the windows match the spec`() {
        assertEquals(8_000L, ConversationLoop.FOLLOW_UP_MS)
        assertEquals(700L, ConversationLoop.END_OF_TURN_SILENCE_MS)
    }
}
