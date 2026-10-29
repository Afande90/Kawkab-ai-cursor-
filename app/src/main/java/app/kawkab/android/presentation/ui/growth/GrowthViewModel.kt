package app.kawkab.android.presentation.ui.growth

import androidx.lifecycle.ViewModel
import app.kawkab.android.domain.harness.CachedSkill
import app.kawkab.android.domain.harness.SkillCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The Growth screen: successful plans, ranked, and deletable.
 */
@HiltViewModel
class GrowthViewModel @Inject constructor(private val cache: SkillCache) : ViewModel() {
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
