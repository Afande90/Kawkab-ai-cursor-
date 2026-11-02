package app.kawkab.android.domain.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Restart and deletion must affect the actual replay store. */
class SkillPersistenceTest {
    @Test fun `success survives restart and deletion survives another restart`() {
        var saved = emptyList<CachedSkill>()
        val store = object : SkillPersistence {
            override fun load(): List<CachedSkill> = saved
            override fun save(skills: List<CachedSkill>) { saved = skills }
        }
        SkillCache(store).remember("alarm six", "set_alarm", mapOf("hour" to "6"))
        val restarted = SkillCache(store)
        assertEquals("6", restarted.replay("alarm six")?.args?.get("hour"))
        restarted.remember("alarm six", "set_alarm", mapOf("hour" to "6"))
        assertEquals(2, SkillCache(store).replay("alarm six")?.successes)
        restarted.delete("alarm six")
        assertNull(SkillCache(store).replay("alarm six"))
    }
}
