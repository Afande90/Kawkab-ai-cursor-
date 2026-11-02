package app.kawkab.android.presentation.state

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** A verified assistant transcript must reach the main voice loop, not wait for a Send tap. */
class SpokenHandoffTest {
    @Test fun `spoken handoff survives a cold screen and is independent of composer prefill`() = runTest {
        val relay = ChatEntryRequestRelay()
        relay.spoken("how does this work")
        relay.prefill("draft")
        assertEquals("how does this work", relay.voiceRequests.first())
        assertEquals(ChatEntryRequest.Prefill("draft"), relay.requests.first())
    }
}
