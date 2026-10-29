package app.kawkab.android.presentation.ui.onboarding

import app.kawkab.android.domain.models.DefaultBrain
import app.kawkab.design.screens.onboarding.OnboardingLiteRtModel

/**
 * Gemma 4 build onboarding pre-selects for this phone.
 *
 * Below flagship the pick is E2B. E4B stays on the list either way.
 */
object OnboardingBrainPick {
    /**
     * The model the download step starts on.
     *
     * @param totalRamBytes Total RAM in bytes.
     * @return E4B on a flagship phone, otherwise E2B.
     */
    fun model(totalRamBytes: Long): OnboardingLiteRtModel = if (DefaultBrain.isFlagship(totalRamBytes)) {
        OnboardingLiteRtModel.Gemma4E4B
    } else {
        OnboardingLiteRtModel.Gemma4E2B
    }
}
