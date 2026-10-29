package app.kawkab.design.components.chat

import app.kawkab.design.components.chips.Risk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An outbound card is a tap. It does not ask for the destructive word, and it
 * does not offer "always allow", which would quiet the next call.
 */
class OutboundConfirmationStateTest {

    @Test
    fun `outbound allows a tap without a typed word and without always allow`() {
        assertTrue(HitlConfirmationState.isAllowOnceEnabled(Risk.Outbound, ""))
        assertFalse(HitlConfirmationState.showTypedConfirmRow(Risk.Outbound))
        assertFalse(HitlConfirmationState.showAlwaysAllow(Risk.Outbound))
    }
}
