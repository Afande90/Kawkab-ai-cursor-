package app.kawkab.android.presentation.ui.growth

import androidx.lifecycle.ViewModel
import app.kawkab.android.domain.harness.CachedSkill
import app.kawkab.android.domain.harness.SkillCache
import app.kawkab.android.hands.ContactAliasStore
import app.kawkab.android.hands.ContactMatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The Growth screen: successful plans, ranked, and deletable.
 */
@HiltViewModel
class GrowthViewModel @Inject constructor(
    private val cache: SkillCache,
    private val contactAliases: ContactAliasStore = ContactAliasStore.NONE,
) : ViewModel() {
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
