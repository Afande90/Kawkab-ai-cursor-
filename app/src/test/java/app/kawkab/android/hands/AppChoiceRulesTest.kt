package app.kawkab.android.hands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An ambiguous app label cannot silently pick the first installed app. */
class AppChoiceRulesTest {
    private val options = listOf(AppOption("Offline Maps", "local.maps"), AppOption("City Maps", "city.maps"))

    @Test fun `ordinal and unique spoken names select the expected application`() {
        assertEquals("city.maps", AppChoiceRules.pick("the second", options)?.packageName)
        assertEquals("local.maps", AppChoiceRules.pick("offline", options)?.packageName)
        assertEquals("city.maps", AppChoiceRules.pick("اتنين", options)?.packageName)
    }

    @Test fun `a shared word and out of range ordinal do not guess`() {
        assertNull(AppChoiceRules.pick("maps", options))
        assertNull(AppChoiceRules.pick("9", options))
    }
}
