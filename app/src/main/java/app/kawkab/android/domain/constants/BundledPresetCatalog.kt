package app.kawkab.android.domain.constants

/**
 * Declared presentation order of the bundled pipeline-preset catalogue.
 *
 * Without it the picker renders the presets in `AssetManager.list()` order —
 * alphabetical by filename — which opens the gallery on `clarify_then_act`, a
 * plumbing template, and scatters the onboarding scenarios through the list.
 * The bundled catalogue is the first thing a new user browses, so its order is
 * a product decision rather than a filesystem accident.
 *
 * The order is: the Kawkab assistant first (it is the default chat), then the
 * other scenarios offered during onboarding, then the end-to-end showcases,
 * then the building-block templates someone would start their own pipeline
 * from. The mood-router companion is not listed: it stays in the assets as an
 * importable preset and is hidden from this first-run gallery.
 *
 * Ids not listed here sort **after** every listed one, preserving their
 * relative order — so a preset added without touching this list degrades to
 * "appears last" rather than disappearing. `PipelinePresetCatalogValidationTest`
 * nonetheless requires every user-facing bundled preset to be listed, which
 * turns that degradation into a build failure instead of a silent demotion.
 * Internal presets (`PipelinePreset.isInternal`) are deliberately absent: they
 * never reach a list that needs ordering.
 */
object BundledPresetCatalog {

    /** Stable id of the default assistant preset. The filename stem matches. */
    const val KAWKAB_ASSISTANT_ID: String = "kawkab_assistant"

    /**
     * Bundled preset ids, in the order the picker should present them.
     * Each id is a string literal: the browser editor reads this list as text.
     */
    val DISPLAY_ORDER: List<String> = listOf(
        // Default assistant, then the other onboarding scenarios.
        "kawkab_assistant",
        "styled_translation",
        "share_handler",
        // End-to-end showcases.
        "showcase_full_agent",
        "showcase_research_to_file",
        // Building-block templates.
        "local_only_qa",
        "cloud_assist",
        "routed_local_cloud",
        "clarify_then_act",
        "tool_using_react",
        "multi_step_research",
    )

    /**
     * Sort key for [presetId]: its index in [DISPLAY_ORDER], or [Int.MAX_VALUE]
     * for an unlisted id so it sorts to the end.
     *
     * @param presetId The stable preset id (the bundled filename stem).
     * @return The rank used to order the catalogue.
     */
    fun rankOf(presetId: String): Int = DISPLAY_ORDER.indexOf(presetId).takeIf { it >= 0 } ?: Int.MAX_VALUE
}
