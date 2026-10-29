package app.kawkab.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.kawkab.android.domain.safety.ActionDenylist
import app.kawkab.android.domain.safety.ActivityLog
import app.kawkab.android.domain.safety.ActivityLogEntry
import app.kawkab.android.domain.safety.GuardedTap
import app.kawkab.android.domain.safety.OutboundTapGuard
import app.kawkab.android.domain.safety.ScreenSnapshot
import app.kawkab.android.domain.safety.ScreenWatch
import app.kawkab.android.domain.safety.TapDecision
import app.kawkab.android.domain.safety.TapTarget
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Accessibility service that reads the screen and taps only through [GuardedTap].
 * Every event is checked against the denylist. A blocked screen receives no taps.
 */
@AndroidEntryPoint
class KawkabAccessibilityService : AccessibilityService() {
    @Inject lateinit var guard: OutboundTapGuard

    @Inject lateinit var denylist: ActionDenylist

    @Inject lateinit var screenWatch: ScreenWatch

    @Inject lateinit var activityLog: ActivityLog

    private val traversal = AccessibilityNodeTraversal()
    private var suspended = false

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val snapshot = snapshot(event)
        screenWatch.latest = snapshot
        val blocked = denylist.inspectScreen(snapshot)
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

    override fun onInterrupt() = Unit

    /**
     * Clicks [text] when the tap guard and the denylist allow it.
     */
    fun findAndClick(text: String, confirmed: Boolean): Boolean {
        if (suspended) return false
        val root = rootInActiveWindow ?: return false
        return traversal.findAndClick(root, text) { allow(it, confirmed) }
    }

    /**
     * Clicks [viewId] when the tap guard and the denylist allow it.
     */
    fun findAndClickById(viewId: String, confirmed: Boolean): Boolean {
        if (suspended) return false
        val root = rootInActiveWindow ?: return false
        return traversal.findAndClickById(root, viewId) { allow(it, confirmed) }
    }

    /**
     * The text currently on screen, or empty when the service cannot see it.
     */
    fun currentScreenText(): String = traversal.screenText(rootInActiveWindow)

    private fun allow(target: TapTarget, confirmed: Boolean): Boolean =
        GuardedTap.decision(guard, target, confirmed) == TapDecision.ALLOW

    private fun snapshot(event: AccessibilityEvent): ScreenSnapshot {
        val described = traversal.describe(rootInActiveWindow)
        val password = event.source?.isPassword == true || described.second
        return ScreenSnapshot(
            packageName = event.packageName?.toString().orEmpty(),
            secure = event.isAccessibilityDataSensitive,
            passwordField = password,
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
