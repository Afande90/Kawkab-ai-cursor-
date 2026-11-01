package app.kawkab.android.assistant

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import app.kawkab.android.presentation.ui.MainActivity

/**
 * One way in to Kawkab that always works (task 23k): the assistant panel over the current app
 * when Kawkab holds the assistant role, otherwise a new chat in the app.
 */
object AssistantEntry {
    /** The deep link of a new chat. */
    const val NEW_CHAT_URI: String = "knotwork://new-chat"

    /**
     * @param context Any context.
     * @return `true` when the panel opened, `false` when the app was opened instead.
     */
    fun open(context: Context): Boolean {
        if (KawkabVoiceInteractionService.showPanel()) return true
        context.startActivity(newChatIntent(context))
        return false
    }

    /**
     * @param context Any context.
     * @return The intent that opens a new chat in the app.
     */
    fun newChatIntent(context: Context): Intent = Intent(Intent.ACTION_VIEW, NEW_CHAT_URI.toUri())
        .setClass(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
