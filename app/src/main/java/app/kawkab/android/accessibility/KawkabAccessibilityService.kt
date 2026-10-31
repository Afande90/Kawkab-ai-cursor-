package app.kawkab.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.kawkab.android.domain.safety.ActionDenylist
import app.kawkab.android.domain.safety.ActivityLog
import app.kawkab.android.domain.safety.ActivityLogEntry
import app.kawkab.android.domain.safety.BlockReason
import app.kawkab.android.domain.safety.GuardedTap
import app.kawkab.android.domain.safety.OutboundTapGuard
import app.kawkab.android.domain.safety.ScreenSnapshot
import app.kawkab.android.domain.safety.ScreenWatch
import app.kawkab.android.domain.safety.TapDecision
import app.kawkab.android.domain.safety.TapTarget
import app.kawkab.android.domain.safety.TextInspectionSchedule
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Accessibility service that reads the screen and taps only through [GuardedTap].
 *
 * Per event it keeps only the package, `FLAG_SECURE` and the password state, which are cheap.
 * The screen text is read off the main thread at most every 500 ms, and in full immediately
 * before every action (task 23d). A blocked screen receives no taps.
 */
@AndroidEntryPoint
class KawkabAccessibilityService : AccessibilityService() {
    @Inject lateinit var guard: OutboundTapGuard

    @Inject lateinit var denylist: ActionDenylist

    @Inject lateinit var screenWatch: ScreenWatch

    @Inject lateinit var activityLog: ActivityLog

    private val traversal = AccessibilityNodeTraversal()
    private val schedule = TextInspectionSchedule()
    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null

    @Volatile
    private var suspended = false

    @Volatile
    private var lastSecure = false

    override fun onServiceConnected() {
        instance = this
        val thread = HandlerThread("kawkab-screen-text").apply { start() }
        worker = thread
        workerHandler = Handler(thread.looper)
        screenWatch.fullReader = ::fullSnapshot
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        if (screenWatch.fullReader != null) screenWatch.fullReader = null
        worker?.quitSafely()
        worker = null
        workerHandler = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString().orEmpty()
        lastSecure = event.isAccessibilityDataSensitive
        val previous = screenWatch.latest
        val cheap = ScreenSnapshot(
            packageName = packageName,
            secure = lastSecure,
            passwordField = event.source?.isPassword == true,
            // Text from the last background check stays only while the same app is in front.
            visibleText = if (previous.packageName == packageName) previous.visibleText else "",
        )
        screenWatch.latest = cheap
        update(denylist.inspectScreen(cheap.copy(visibleText = "")))
        val delay = schedule.onEvent(SystemClock.uptimeMillis()) ?: return
        workerHandler?.postDelayed(::inspectText, delay)
    }

    override fun onInterrupt() = Unit

    /** Reads the screen text on the worker thread and runs the full denylist check on it. */
    private fun inspectText() {
        schedule.onRun(SystemClock.uptimeMillis())
        val full = fullSnapshot()
        screenWatch.latest = full
        update(denylist.inspectScreen(full))
    }

    private fun update(blocked: BlockReason?) {
        val nowSuspended = blocked != null
        if (nowSuspended && !suspended && blocked != null) {
            activityLog.record(
                ActivityLogEntry(
                    asked = "",
                    ran = "screen",
                    entryPoint = "accessibility",
                    result = "blocked: ${blocked.why}",
                    confirmation = blocked.layer.name,
                    atEpochMs = System.currentTimeMillis(),
                ),
            )
        }
        suspended = nowSuspended
    }

    /**
     * Clicks [text] when the tap guard and the denylist allow it.
     */
    fun findAndClick(text: String, confirmed: Boolean): Boolean {
        if (blockedNow()) return false
        val root = rootInActiveWindow ?: return false
        return traversal.findAndClick(root, text) { allow(it, confirmed) }
    }

    /**
     * Clicks [viewId] when the tap guard and the denylist allow it.
     */
    fun findAndClickById(viewId: String, confirmed: Boolean): Boolean {
        if (blockedNow()) return false
        val root = rootInActiveWindow ?: return false
        return traversal.findAndClickById(root, viewId) { allow(it, confirmed) }
    }

    /**
     * The text currently on screen, or empty when the service cannot see it.
     */
    fun currentScreenText(): String = traversal.screenText(rootInActiveWindow)

    /** The full denylist check, immediately before an action (§0.4.4). */
    private fun blockedNow(): Boolean {
        val blocked = denylist.inspectScreen(fullSnapshot())
        update(blocked)
        return blocked != null
    }

    private fun allow(target: TapTarget, confirmed: Boolean): Boolean =
        GuardedTap.decision(guard, target, confirmed) == TapDecision.ALLOW

    /** The whole screen, text included. Walks the node tree, so it is never called per event. */
    private fun fullSnapshot(): ScreenSnapshot {
        val root = rootInActiveWindow
        val described = traversal.describe(root)
        return ScreenSnapshot(
            packageName = root?.packageName?.toString() ?: screenWatch.latest.packageName,
            secure = lastSecure,
            passwordField = described.second,
            visibleText = described.first,
        )
    }

    companion object {
        private var instance: KawkabAccessibilityService? = null

        private val sendLabels = listOf("Send", "send", "SEND", "إرسال", "ارسال", "ابعت")
        private val smsSendIds = listOf(
            "com.google.android.apps.messaging:id/send_message_button",
            "com.google.android.apps.messaging:id/send_message_button_icon",
            "com.samsung.android.messaging:id/send_button",
            "com.android.mms:id/send_button",
            "com.android.messaging:id/send_message_button",
        )
        private val whatsAppSendIds = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp:id/send_button",
        )
        private val telegramSendIds = listOf(
            "org.telegram.messenger:id/chat_send_button",
            "org.telegram.messenger.web:id/chat_send_button",
        )
        private val stubSendIds = listOf("app.kawkab.stubmessenger:id/send_button")

        /**
         * Taps a send control. [confirmed] must already be the owner's tap.
         */
        fun tapSend(confirmed: Boolean): Boolean {
            val service = instance ?: return false
            val ids = smsSendIds + whatsAppSendIds + telegramSendIds + stubSendIds
            if (ids.any { service.findAndClickById(it, confirmed) }) return true
            return sendLabels.any { service.findAndClick(it, confirmed) }
        }

        /**
         * Taps [text] or [viewId].
         */
        fun tap(text: String, viewId: String, confirmed: Boolean): Boolean {
            val service = instance ?: return false
            if (viewId.isNotBlank() && service.findAndClickById(viewId, confirmed)) return true
            return text.isNotBlank() && service.findAndClick(text, confirmed)
        }

        /**
         * Types [content] into the focused editable field.
         */
        fun typeIntoFocused(content: String): Boolean {
            val service = instance ?: return false
            if (service.blockedNow()) return false
            val root = service.rootInActiveWindow ?: return false
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, content)
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        /**
         * Text of the current window.
         */
        fun screenText(): String = instance?.currentScreenText().orEmpty()
    }
}
