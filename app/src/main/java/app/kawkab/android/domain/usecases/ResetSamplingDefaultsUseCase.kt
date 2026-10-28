package app.kawkab.android.domain.usecases

import app.kawkab.android.domain.repositories.SettingsReset
import javax.inject.Inject

/**
 * Resets the local-generation sampling parameters back to
 * [app.kawkab.android.domain.constants.SettingsDefaults] values. Backs the
 * "Reset to defaults" header action inside the Settings → LLM parameters
 * card.
 *
 * Thin wrapper over [SettingsReset.resetSamplingDefaults] so callers
 * can express intent through a typed use case (and so the surface is
 * mockable in `SettingsViewModelTest`).
 */
class ResetSamplingDefaultsUseCase @Inject constructor(private val settingsReset: SettingsReset) {
    /** Resets temperature / top-K / top-P / max-context / max-steps. */
    suspend operator fun invoke() {
        settingsReset.resetSamplingDefaults()
    }
}
