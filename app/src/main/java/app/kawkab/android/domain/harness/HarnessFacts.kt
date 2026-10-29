package app.kawkab.android.domain.harness

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facts the owner stated, injected into every prompt.
 */
@Singleton
class HarnessFacts @Inject constructor() {
    private val items = MutableStateFlow<List<String>>(emptyList())

    /**
     * Remembers one stated fact. A blank fact is ignored.
     */
    fun state(fact: String) {
        val clean = fact.trim()
        if (clean.isEmpty()) return
        items.update { it + clean }
    }

    /**
     * The facts in the order they were stated.
     */
    fun snapshot(): List<String> = items.value
}
