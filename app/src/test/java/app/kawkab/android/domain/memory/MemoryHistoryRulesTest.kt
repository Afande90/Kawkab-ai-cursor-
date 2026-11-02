package app.kawkab.android.domain.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Explicit changes and uncertain conflicts cannot be collapsed into the same write. */
class MemoryHistoryRulesTest {
    @Test fun `single valued changes require explicit change words`() {
        assertTrue(MemoryHistoryRules.explicitChange("My home is in Cairo", "I moved home to Khartoum"))
        assertTrue(MemoryHistoryRules.explicitChange("عندي عربية حمراء", "بعت العربية"))
        assertFalse(MemoryHistoryRules.explicitChange("My phone is blue", "My phone is black"))
        assertFalse(MemoryHistoryRules.explicitChange("My home is in Cairo", "I might have moved home"))
    }

    @Test fun `unrelated topics and uncertain language cannot retire a fact`() {
        assertFalse(MemoryHistoryRules.explicitChange("My car is red", "I moved home"))
        assertTrue(MemoryHistoryRules.needsExplicitChange("My job is teaching", "My job is design"))
        assertFalse(MemoryHistoryRules.needsExplicitChange("I like tea", "I prefer coffee"))
    }
}
