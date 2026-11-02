package app.kawkab.android.hands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Task 34b and vision rule 2: a pick is remembered and never asked again for that name. */
class RememberedContactTest {
    private class MemoryAliases : ContactAliasStore {
        val map = mutableMapOf<String, ContactMatch>()
        override fun get(spoken: String) = map[ContactAliasStore.key(spoken)]
        override fun remember(spoken: String, contact: ContactMatch) {
            map[ContactAliasStore.key(spoken)] = contact
        }
        override fun all() = map.toMap()
        override fun forget(spoken: String) {
            map.remove(ContactAliasStore.key(spoken))
        }
    }

    @Test
    fun `picking the second abdalla remembers him for abdalla`() {
        val aliases = MemoryAliases()
        val choice = ContactChoice(aliases)
        val mubadala = ContactMatch("Abdalla Mubadala", "0911006117")
        choice.offer(
            "call_phone",
            mapOf(ContactChoice.SPOKEN to "Abdalla"),
            listOf(ContactMatch("Abdalla Osman", "0922"), mubadala),
            nowMs = 0,
        )
        choice.answer("the second one", nowMs = 1_000)
        assertEquals(mubadala, choice.remembered("abdalla"))
        assertEquals(mubadala, choice.remembered("the Abdalla"))
        assertNull(choice.remembered("omar"))
    }

    @Test
    fun `a forgotten name asks again`() {
        val aliases = MemoryAliases()
        aliases.remember("abdalla", ContactMatch("Abdalla Mubadala", "0911"))
        aliases.forget("Abdalla")
        assertNull(ContactChoice(aliases).remembered("abdalla"))
    }
}
