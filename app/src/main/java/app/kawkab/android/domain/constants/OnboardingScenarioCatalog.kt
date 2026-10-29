package app.kawkab.android.domain.constants

import app.kawkab.android.domain.models.EntrySurface

/**
 * Canonical wiring for the onboarding value-gallery scenarios.
 *
 * Each scenario the user can pick on the "Choose a scenario" step maps to a
 * concrete set-up recipe on the domain side:
 *  - the bundled preset that is materialised as the user's default pipeline
 *    (`assets/presets/pipelines/<presetId>.json`, keyed by its stable slug id);
 *  - the OS [EntrySurface] the scenario binds, or `null` when it runs purely
 *    from the chat surface.
 *
 * The **model** a scenario needs is *not* duplicated here: it lives on the
 * design-system `OnboardingScenario.requiredModel` enum, which the UI and the
 * `OnboardingViewModel` already read to pre-select and frame the download. This
 * object owns only what the domain layer acts on (preset + surface); the two
 * enums stay decoupled and are joined by the stable [Spec.id] string.
 *
 * The featured card is the Kawkab assistant. The mood-router companion is not
 * an onboarding scenario; its preset file stays importable and is hidden from
 * the first-run gallery.
 *
 * Used by [app.kawkab.android.domain.usecases.SetUpScenarioUseCase] to resolve
 * a picked scenario id into the preset / surface it materialises.
 */
object OnboardingScenarioCatalog {

    /** Stable id of the on-device styled-translation scenario (first / default card). */
    const val ID_STYLED_TRANSLATION: String = "styled_translation"

    /** Stable id of the share-capture scenario (binds the system Share sheet). */
    const val ID_SHARE_HANDLER: String = "share_handler"

    /**
     * Stable id of the featured card. It materialises the Kawkab assistant.
     * The constant name is historical: the card used to be the mood router.
     */
    const val ID_VIRTUAL_COMPANION: String = BundledPresetCatalog.KAWKAB_ASSISTANT_ID

    /** Stable id of the Kawkab assistant scenario. Same preset as [ID_VIRTUAL_COMPANION]. */
    const val ID_KAWKAB_ASSISTANT: String = BundledPresetCatalog.KAWKAB_ASSISTANT_ID

    /**
     * The materialisation recipe for a single onboarding scenario.
     *
     * @property id stable scenario id (matches the design-system
     *   `OnboardingScenario.id` and the bundled preset filename stem).
     * @property presetId id of the bundled preset materialised as the default
     *   pipeline. Identical to [id] today — kept as a distinct property so a
     *   scenario could later point at a differently-named preset without
     *   changing the UI-facing scenario key.
     * @property entrySurface OS surface the scenario binds to the materialised
     *   pipeline, or `null` when the scenario is driven only from chat.
     */
    data class Spec(val id: String, val presetId: String, val entrySurface: EntrySurface?)

    /**
     * Every onboarding scenario recipe, in gallery order. The Kawkab assistant
     * is last and featured.
     */
    val SCENARIOS: List<Spec> = listOf(
        Spec(id = ID_STYLED_TRANSLATION, presetId = ID_STYLED_TRANSLATION, entrySurface = null),
        Spec(id = ID_SHARE_HANDLER, presetId = ID_SHARE_HANDLER, entrySurface = EntrySurface.SHARE),
        Spec(id = ID_KAWKAB_ASSISTANT, presetId = ID_KAWKAB_ASSISTANT, entrySurface = null),
    )

    /** Returns the recipe for [scenarioId], or `null` for an unknown id. */
    fun byId(scenarioId: String): Spec? = SCENARIOS.firstOrNull { it.id == scenarioId }
}
