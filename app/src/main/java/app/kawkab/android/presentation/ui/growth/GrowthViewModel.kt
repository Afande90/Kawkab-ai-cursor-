package app.kawkab.android.presentation.ui.growth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kawkab.android.domain.harness.CachedSkill
import app.kawkab.android.domain.harness.SkillCache
import app.kawkab.android.domain.models.MemoryChunk
import app.kawkab.android.domain.models.MemoryVersion
import app.kawkab.android.domain.repositories.MemoryHistoryRepository
import app.kawkab.android.domain.repositories.MemoryRepository
import app.kawkab.android.hands.ContactAliasStore
import app.kawkab.android.hands.ContactMatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Growth screen: successful plans, ranked, and deletable.
 */
@HiltViewModel
class GrowthViewModel @Inject constructor(
    private val cache: SkillCache,
    private val contactAliases: ContactAliasStore = ContactAliasStore.NONE,
    private val memory: MemoryRepository? = null,
    private val history: MemoryHistoryRepository? = null,
) : ViewModel() {
    /** Every learned fact is visible on Growth, including preserved historical statements. */
    val memories = MutableStateFlow<List<MemoryChunk>>(emptyList())

    /** Earlier statements remain linked to their current fact and are independently removable. */
    val past = MutableStateFlow<List<MemoryVersion>>(emptyList())

    /** Reload when Growth opens so idle extraction appears here. */
    fun refresh() {
        aliases.value = contactAliases.all()
        viewModelScope.launch(Dispatchers.IO) {
            memories.value = memory?.getAllMemories().orEmpty()
            past.value = history?.getAllHistory()?.values?.flatten().orEmpty()
        }
    }

    /** The device shell obtains a fresh spoken yes before this operation. */
    fun forgetMemory(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            memory?.deleteMemory(id)
            memories.value = memory?.getAllMemories().orEmpty()
        }
    }

    /** Removes only one earlier version after the shell obtains spoken consent. */
    fun forgetPast(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            history?.deleteVersion(id)
            past.value = history?.getAllHistory()?.values?.flatten().orEmpty()
        }
    }

    /** Names the owner resolved, all visible and removable. */
    val aliases: MutableStateFlow<Map<String, ContactMatch>> = MutableStateFlow(contactAliases.all())

    /** Forgets a contact choice so that the next ambiguous request asks again. */
    fun forgetContact(name: String) {
        contactAliases.forget(name)
        aliases.value = contactAliases.all()
    }

    /**
     * Plans with the most successes first.
     */
    val skills: StateFlow<List<CachedSkill>> = cache.ranked

    /**
     * Removes one saved plan.
     */
    fun delete(key: String) {
        cache.delete(key)
    }
}
