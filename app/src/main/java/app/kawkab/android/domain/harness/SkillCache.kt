package app.kawkab.android.domain.harness

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A plan that succeeded, so the next identical request can replay it with no model call.
 *
 * @property key The owner's sentence.
 * @property tool The tool that succeeded.
 * @property args Arguments that succeeded with it.
 * @property successes How many times this plan has succeeded.
 */
data class CachedSkill(val key: String, val tool: String, val args: Map<String, String>, val successes: Int)

/**
 * Successful plans, ranked by how often they succeeded. The Growth screen deletes them.
 */
@Singleton
class SkillCache @Inject constructor(private val persistence: SkillPersistence) {
    /** Isolated tests use no persistent phone state. */
    constructor() : this(SkillPersistence.NONE)

    private val items = MutableStateFlow(persistence.load())

    /**
     * Plans with the most successes first.
     */
    val ranked: StateFlow<List<CachedSkill>> = items.asStateFlow()

    /**
     * Stores a successful plan. The same [key] adds one success.
     */
    fun remember(key: String, tool: String, args: Map<String, String>) {
        val clean = key.trim()
        if (clean.isEmpty() || tool.isBlank()) return
        items.update { current ->
            val next = current.filterNot { it.key == clean }
            val previous = current.firstOrNull { it.key == clean }?.successes ?: 0
            (next + CachedSkill(clean, tool, args, previous + 1)).sortedByDescending { it.successes }
        }
        persistence.save(items.value)
    }

    /**
     * The stored plan for [key], or null. This does not call a model and does not add a success.
     */
    fun replay(key: String): CachedSkill? = items.value.firstOrNull { it.key == key.trim() }

    /**
     * Removes [key]. A missing key is left alone.
     */
    fun delete(key: String) {
        items.update { current -> current.filterNot { it.key == key } }
        persistence.save(items.value)
    }
}

/** Local durable storage; tests can supply a separate in-memory store. */
interface SkillPersistence {
    /** Reads the last committed successful plans. */
    fun load(): List<CachedSkill>

    /** Atomically replaces the saved plans. */
    fun save(skills: List<CachedSkill>)

    /** Defaults for isolated routing tests. */
    companion object {
        /** No storage for a standalone test instance. */
        val NONE = object : SkillPersistence {
            override fun load(): List<CachedSkill> = emptyList()
            override fun save(skills: List<CachedSkill>) = Unit
        }
    }
}
