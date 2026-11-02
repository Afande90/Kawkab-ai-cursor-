package app.kawkab.android.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.kawkab.android.R
import app.kawkab.android.assistant.KawkabVoiceInteractionService
import app.kawkab.android.domain.constants.NotificationIds
import app.kawkab.android.presentation.notifications.lockScreenVersion
import app.kawkab.android.presentation.ui.MainActivity

/**
 * The app-process side of the wake word (M1.8). [WakeWordService] runs in its own process, so it
 * sends what happened here: a wake (open the panel, count it), a failure (turn the wake word off
 * with its reason), or the notification's Stop. Not exported; only this app's own components
 * reach it.
 */
class WakeRelayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_WOKE -> woke(context)
            ACTION_FAILED -> WakeWordSwitch.failed(context, intent.getStringExtra(EXTRA_REASON) ?: "error")
            ACTION_STOP -> WakeWordSwitch.set(context, false)
        }
    }

    private fun woke(context: Context) {
        WakeWordSwitch.countWake(context)
        if (WakeWordSwitch.isCharging(context)) WakeWordSwitch.noteCharging(context)
        val args = Bundle().apply { putBoolean(WakeWordService.EXTRA_WAKE, true) }
        if (KawkabVoiceInteractionService.showPanel(args)) return
        // Not the assistant: Android does not let the background open a screen, so a notification
        // asks for one tap instead, and listening resumes after a moment.
        notify(context, tapToTalk(context))
        val pending = goAsync()
        Handler(Looper.getMainLooper()).postDelayed({
            WakeWordService.resume(context)
            pending.finish()
        }, NO_PANEL_RESUME_MS)
    }

    private fun tapToTalk(context: Context): Notification = NotificationCompat.Builder(context, WakeWordService.CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_agent)
        .setContentTitle(context.getString(R.string.wake_heard_title))
        .setContentText(context.getString(R.string.wake_heard_body))
        .setAutoCancel(true)
        .setTimeoutAfter(HEARD_TIMEOUT_MS)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .lockScreenVersion(
            context,
            WakeWordService.CHANNEL,
            R.drawable.ic_stat_agent,
            context.getString(R.string.wake_heard_title),
        )
        .build()

    private fun notify(context: Context, notification: Notification) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            context.getSystemService(
                NotificationManager::class.java,
            )?.notify(NotificationIds.WAKE_WORD_HEARD, notification)
        }
    }

    /** Actions and the sender used by the wake process. */
    companion object {
        /** "Kawkab" was heard. */
        const val ACTION_WOKE: String = "app.kawkab.android.WAKE_RELAY_WOKE"

        /** The wake word could not start; [EXTRA_REASON] says why. */
        const val ACTION_FAILED: String = "app.kawkab.android.WAKE_RELAY_FAILED"

        /** The listening notification's Stop. */
        const val ACTION_STOP: String = "app.kawkab.android.WAKE_RELAY_STOP"

        private const val EXTRA_REASON = "reason"
        private const val NO_PANEL_RESUME_MS = 3_000L
        private const val HEARD_TIMEOUT_MS = 30_000L

        /**
         * Sends [action] to the app process.
         *
         * @param context The wake process.
         * @param action One of the actions above.
         * @param reason For [ACTION_FAILED].
         */
        fun send(context: Context, action: String, reason: String? = null) {
            context.sendBroadcast(
                Intent(context, WakeRelayReceiver::class.java).setAction(action).apply {
                    reason?.let { putExtra(EXTRA_REASON, it) }
                },
            )
        }
    }
}
