package app.kawkab.android.hands

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import app.kawkab.android.accessibility.KawkabAccessibilityService
import app.kawkab.android.domain.safety.ActionDenylist
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intent and accessibility implementation of [PhoneHands].
 * Calls use ACTION_CALL, which needs CALL_PHONE and only runs after the outbound tap.
 * Messages use ACTION_SENDTO. There is no SEND_SMS permission.
 */
@Singleton
class AndroidPhoneHands @Inject constructor(
    @ApplicationContext private val context: Context,
    private val denylist: ActionDenylist,
) : PhoneHands {

    override fun placeCall(number: String): String {
        val uri = "tel:${Uri.encode(number)}"
        return start(Intent(Intent.ACTION_CALL, Uri.parse(uri)), "Called $number.")
    }

    override fun composeSms(number: String, body: String): String {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
            putExtra("sms_body", body)
        }
        val opened = start(intent, "Opened the message to $number.")
        if (opened.startsWith("Blocked")) return opened
        val tapped = KawkabAccessibilityService.tapSend(confirmed = true)
        return if (tapped) "Sent the message to $number." else "$opened The send control was not on screen yet."
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
        return start(intent, "Alarm set for $hour:$minute.")
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
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startEpochMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startEpochMs + ONE_HOUR_MS)
            putExtra(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        return start(intent, "Calendar event '$title' is ready to save.")
    }

    override fun readScreen(): String = KawkabAccessibilityService.screenText()

    override fun tap(text: String, viewId: String, confirmed: Boolean): String {
        val clicked = KawkabAccessibilityService.tap(text, viewId, confirmed)
        return if (clicked) "Tapped." else "Did not tap. The control is missing, or it still needs a tap."
    }

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
