package app.kawkab.android.presentation.ui.pocket

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Failed voice commands need a visible explanation without exposing the engine. */
class PocketErrorsTest {
    @Test fun `model failures direct the owner to Models without leaking engine details`() {
        val text = PocketErrors.reply("Native engine failed loading private-model.gguf")
        assertTrue(text.contains("Models"))
        assertFalse(text.contains("gguf"))
        assertFalse(text.contains("Native"))
    }
    @Test fun `permissions and locked phones receive useful separate instructions`() {
        assertTrue(PocketErrors.reply("Missing permission android.permission.CALL_PHONE").contains("Phone access"))
        assertTrue(PocketErrors.reply("Phone is locked").contains("Unlock"))
    }
}
