package app.kawkab.android.voice

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import androidx.core.content.edit

/**
 * The owner's "Confirm by voice" setting (task 29, spec §0.4.3). Off by default and turned on only
 * behind a warning that a TV or a recording could say "yes". Even on, `VoiceAnswers.decide` accepts
 * a spoken yes only with the screen on and the phone unlocked.
 */
object VoiceConfirmSwitch {
    private const val FILE = "voice_confirm"
    private const val ENABLED = "enabled"

    /** @return Whether the owner turned it on. */
    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    /** @param on The new setting. */
    fun set(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(ENABLED, on) }
    }

    /** @return Whether the screen is on. */
    fun screenOn(context: Context): Boolean = context.getSystemService(PowerManager::class.java)?.isInteractive == true

    /** @return Whether the phone is locked; unknown counts as locked. */
    fun locked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked != false
}
