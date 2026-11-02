package app.kawkab.android.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import app.kawkab.android.R
import app.kawkab.android.domain.assistant.DefaultAssistantGuide

/**
 * Builds the system intent that asks the owner to make Kawkab the digital assistant.
 */
object AssistantRoleRequest {
    /**
     * The role-request intent, or null when the role is missing or already held.
     */
    fun create(context: Context): Intent? {
        val roles = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return null
        if (roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)) return null
        return roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
    }

    /**
     * Whether Kawkab really holds the assistant role (task 23k).
     *
     * @param context Any context.
     * @return `true` when `RoleManager.isRoleHeld(ROLE_ASSISTANT)`.
     */
    fun isHeld(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true

    /**
     * The screen that lets the owner pick the assistant when the role request is not offered:
     * the voice-input settings, else the default-apps screen.
     *
     * @param context Any context.
     * @return An intent that resolves on this phone.
     */
    fun settingsFallback(context: Context): Intent {
        val voice = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (voice.resolveActivity(context.packageManager) != null) return voice
        return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Opens a power-button gesture page when the phone exports one, else the assistant settings. */
    fun powerButtonSettings(context: Context): Intent {
        val screens = listOf(
            "com.android.settings.Settings\$LongPressPowerButtonActivity",
            "com.android.settings.Settings\$PowerMenuSettingsActivity",
            "com.android.settings.Settings\$GestureSettingsActivity",
        )
        return screens.firstNotNullOfOrNull { screen ->
            val intent = Intent().setClassName("com.android.settings", screen)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.takeIf { it.resolveActivityInfo(context.packageManager, 0)?.exported == true }
        } ?: settingsFallback(context)
    }

    /**
     * The default-assistant steps in the owner's language.
     *
     * Same order as [DefaultAssistantGuide.steps].
     */
    fun steps(context: Context): List<String> = listOf(
        context.getString(R.string.assistant_step_settings),
        context.getString(R.string.assistant_step_choose),
        context.getString(R.string.assistant_step_confirm),
    )
}
