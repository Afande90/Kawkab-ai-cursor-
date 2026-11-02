package app.kawkab.android.hands

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.kawkab.android.accessibility.KawkabAccessibilityService
import app.kawkab.android.domain.phoneaccess.MissingPermission
import app.kawkab.android.domain.phoneaccess.MissingPermissionCard
import app.kawkab.android.domain.safety.ActionDenylist
import app.kawkab.android.domain.text.ClockText
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intent and accessibility implementation of [PhoneHands].
 * Calls use Telecom, with a default-dialer-only fallback, after the announced cancel window.
 * Messages use Android SMS after an explicit system permission grant.
 */
@Singleton
class AndroidPhoneHands @Inject constructor(
    @ApplicationContext private val context: Context,
    private val denylist: ActionDenylist,
) : PhoneHands {

    override fun placeCall(number: String): String {
        val uri = "tel:${Uri.encode(number)}"
        // Without CALL_PHONE the dialer opens with the number filled in, which needs no
        // permission; the owner presses call. A call never fails with only an error (task 23j).
        if (CallFallback.usesDialer(MissingPermission.CALL_PHONE in grantedNow())) {
            return start(Intent(Intent.ACTION_DIAL, uri.toUri()), CallFallback.DIALER_OPENED)
        }
        placeThroughTelecom(number)?.let { return it }
        val dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
            ?: return "No default Phone app is set. Choose one in Android settings and try again."
        return start(Intent(Intent.ACTION_CALL, uri.toUri()).setPackage(dialer), "Called $number.")
    }

    /**
     * Places the call through the phone's own call service, so no app chooser appears and no other
     * app that handles `tel:` (Zoom and the like) can take it (task 34b).
     *
     * @return The result, or `null` when the call service is unavailable and the intent is used.
     */
    private fun placeThroughTelecom(number: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return null
        val uri = Uri.fromParts("tel", number, null)
        denylist.inspectIntent(null, uri.toString())?.let { return "Blocked (${it.layer}): ${it.why}" }
        return try {
            telecom.placeCall(uri, Bundle())
            "Called $number."
        } catch (_: SecurityException) {
            null
        }
    }

    override fun composeSms(number: String, body: String): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return MissingPermissionCard(MissingPermission.SEND_SMS, "send_sms").sentence()
        }
        denylist.inspectIntent(null, "smsto:$number")?.let { return "Blocked (${it.layer}): ${it.why}" }
        return try {
            val subscription = SubscriptionManager.getDefaultSmsSubscriptionId()
            if (subscription < 0) return "Choose a default SMS SIM in Android settings, then try again."
            val manager = context.getSystemService(SmsManager::class.java)?.createForSubscriptionId(subscription)
                ?: return "SMS is unavailable on this phone."
            val parts = manager.divideMessage(body)
            if (parts.size == 1) {
                manager.sendTextMessage(number, null, body, null, null)
            } else {
                manager.sendMultipartTextMessage(number, null, parts, null, null)
            }
            "SMS submitted to Android for $number. Delivery is confirmed by your mobile network."
        } catch (_: Exception) {
            "I could not send that SMS. Check your SIM and phone access settings."
        }
    }

    override fun sendInApp(packageName: String, body: String): String {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return "App $packageName is not installed."
        val opened = start(launch, "Opened $packageName.")
        if (opened.startsWith("Blocked")) return opened
        val typed = KawkabAccessibilityService.typeIntoFocused(body)
        val tapped = KawkabAccessibilityService.tapSend(confirmed = true)
        return if (typed && tapped) "Sent in $packageName." else "$opened The send control was not on screen yet."
    }

    override fun setAlarm(hour: Int, minute: Int, label: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        return start(intent, "Alarm set for ${ClockText.format(hour, minute, Locale.getDefault())}.")
    }

    override fun setTimer(seconds: Int): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        return start(intent, "Timer set for $seconds seconds.")
    }

    override fun openApp(name: String): String {
        val launch = resolveLaunch(name) ?: return "No app named '$name' is installed."
        return start(launch, "Opened $name.")
    }

    override fun addCalendarEvent(title: String, startEpochMs: Long): String {
        MissingPermission.card("add_calendar_event", grantedNow())?.let { return it.sentence() }
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startEpochMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startEpochMs + ONE_HOUR_MS)
            putExtra(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        return start(intent, "Calendar event '$title' is ready to save.")
    }

    override fun undo(tool: String, args: Map<String, String>): String = when (tool) {
        // Dismisses the alarm just set, found by its time (task 23i).
        "set_alarm" -> start(
            Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
                args["hour"]?.toIntOrNull()?.let { putExtra(AlarmClock.EXTRA_HOUR, it) }
                args["minute"]?.toIntOrNull()?.let { putExtra(AlarmClock.EXTRA_MINUTES, it) }
            },
            "Removed the alarm.",
        )
        "set_timer" -> start(Intent(AlarmClock.ACTION_DISMISS_TIMER), "Stopped the timer.")
        "open_app" -> start(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            "Went back to the home screen.",
        )
        "add_calendar_event" -> "The event is not saved until you tap Save; close the calendar screen to undo."
        else -> "Nothing to undo."
    }

    override fun readScreen(): String = KawkabAccessibilityService.screenText()

    override fun tap(text: String, viewId: String, confirmed: Boolean): String {
        val clicked = KawkabAccessibilityService.tap(text, viewId, confirmed)
        return if (clicked) "Tapped." else "Did not tap. The control is missing, or it still needs a tap."
    }

    private fun grantedNow(): Set<String> = listOf(
        MissingPermission.CALL_PHONE,
        MissingPermission.SEND_SMS,
        MissingPermission.READ_CONTACTS,
        MissingPermission.WRITE_CALENDAR,
    ).filter { name ->
        ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
    }.toSet()

    private fun start(intent: Intent, success: String): String {
        val blocked = denylist.inspectIntent(intent.`package`, intent.dataString)
        if (blocked != null) return "Blocked (${blocked.layer}): ${blocked.why}"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            success
        } catch (error: Exception) {
            "Could not start that action: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    private fun resolveLaunch(name: String): Intent? {
        val byPackage = context.packageManager.getLaunchIntentForPackage(name)
        if (byPackage != null) return byPackage
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = context.packageManager.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
        val match = apps.firstOrNull { info ->
            val label = info.loadLabel(context.packageManager).toString()
            label.equals(name, ignoreCase = true)
        } ?: return null
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(match.activityInfo.packageName, match.activityInfo.name)
        }
    }

    private companion object {
        const val ONE_HOUR_MS = 3_600_000L
    }
}
