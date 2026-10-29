package app.kawkab.android.domain.assistant

/**
 * String names for the steps that make Kawkab the phone's digital assistant.
 *
 * The phone-access screen shows these. The wording lives in the string resources, in English and Arabic.
 */
object DefaultAssistantGuide {
    /**
     * Open the system digital-assistant setting.
     */
    const val STEP_SETTINGS: String = "assistant_step_settings"

    /**
     * Choose Kawkab in that setting.
     */
    const val STEP_CHOOSE: String = "assistant_step_choose"

    /**
     * Confirm the system prompt, then the power-button gesture opens the panel.
     */
    const val STEP_CONFIRM: String = "assistant_step_confirm"

    /**
     * The steps, in the order the owner follows them.
     */
    val steps: List<String> = listOf(STEP_SETTINGS, STEP_CHOOSE, STEP_CONFIRM)
}
