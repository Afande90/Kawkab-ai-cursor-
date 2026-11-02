package app.kawkab.android.domain.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Generic taps and macros cannot bypass spoken-only deletion through an ordinary confirmation flag. */
class DeletionIntentTest {
    @Test fun `delete labels and identifiers require spoken evidence in either language`() {
        assertTrue(DeletionIntent.target("Delete photo", ""))
        assertTrue(DeletionIntent.target("حذف", ""))
        assertTrue(DeletionIntent.target("Confirm", "app:id/delete_button"))
        assertFalse(DeletionIntent.target("Send", "app:id/send"))
    }

    @Test fun `a nullable Java thread local never supplies deletion consent`() {
        val field = DeletionConsent::class.java.getDeclaredField("permitted").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val state = field.get(null) as ThreadLocal<Boolean?>
        state.set(null)
        try {
            assertFalse(DeletionConsent.allowed())
            DeletionConsent.withOwnerConsent(true) { assertTrue(DeletionConsent.allowed()) }
            assertFalse(DeletionConsent.allowed())
        } finally {
            state.remove()
        }
    }

    @Test fun `a temporary permit is scoped to the synchronous verified action`() {
        assertFalse(DeletionConsent.allowed())
        DeletionConsent.withOwnerConsent(true) { assertTrue(DeletionConsent.allowed()) }
        assertFalse(DeletionConsent.allowed())
    }
}
