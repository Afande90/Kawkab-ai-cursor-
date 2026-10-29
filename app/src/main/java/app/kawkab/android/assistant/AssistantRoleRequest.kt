package app.kawkab.android.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
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
