package app.kawkab.android.assistant

import android.annotation.SuppressLint
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.graphics.toColorInt
import app.kawkab.android.R
import app.kawkab.android.domain.assistant.AssistantCommandRunner
import app.kawkab.android.domain.assistant.AssistantModelRelease
import app.kawkab.android.domain.assistant.AssistantPlan
import app.kawkab.android.domain.assistant.AssistantPlanner
import app.kawkab.android.domain.assistant.ScreenTextCollector
import app.kawkab.android.domain.assistant.ScreenTextPolicy
import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.models.AgentOrchestratorState
import app.kawkab.android.domain.phoneaccess.MissingPermission
import app.kawkab.android.domain.repositories.PrivacySettings
import app.kawkab.android.presentation.state.ActiveSessionTracker
import app.kawkab.android.presentation.state.ChatEntryRequestRelay
import app.kawkab.android.presentation.ui.MainActivity
import app.kawkab.android.presentation.ui.phoneaccess.AndroidPhoneAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The panel a long-press of the power button opens over the current app.
 *
 * A grammar match goes through the tap gate, and approve and deny sit on this panel.
 * Anything else opens the main chat. Screen text is read only after the owner allows it and asks.
 */
class KawkabAssistantSession(
    private val appContext: Context,
    private val runner: AssistantCommandRunner,
    private val privacy: PrivacySettings,
    private val engine: LlmInferenceEngine,
    private val sessions: ActiveSessionTracker,
    private val relay: ChatEntryRequestRelay,
) : VoiceInteractionSession(appContext) {
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val release = AssistantModelRelease(
        scope = releaseScope,
        chatOpen = { sessions.activeSessionId.value != null },
        unload = { engine.unload() },
    )
    private var structure: AssistStructure? = null
    private var pendingRequestId: String? = null
    private var busy = false
    private var writingSwitch = false
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var screenText: TextView
    private lateinit var approve: Button
    private lateinit var deny: Button
    private lateinit var grant: Button
    private lateinit var screenSwitch: Switch

    override fun onCreateContentView(): View = buildPanel()

    override fun onShow(args: android.os.Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        release.shown()
    }

    override fun onHide() {
        release.hidden()
        super.onHide()
    }

    override fun onHandleAssist(state: AssistState) {
        structure = state.assistStructure
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private fun buildPanel(): View {
        val root = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(PADDING_DP), dp(PADDING_DP), dp(PADDING_DP), dp(PADDING_DP))
            setBackgroundColor(GROUND.toColorInt())
            gravity = Gravity.BOTTOM
        }
        root.addView(title(appContext.getString(R.string.assistant_panel_title)))
        input = EditText(appContext).apply {
            hint = appContext.getString(R.string.assistant_panel_hint)
            setTextColor(Color.WHITE)
            setHintTextColor(MUTED.toColorInt())
        }
        status = body("")
        screenText = body("")
        screenSwitch = Switch(appContext).apply {
            text = appContext.getString(R.string.assistant_use_screen)
            setTextColor(Color.WHITE)
        }
        approve = button(appContext.getString(R.string.assistant_approve), AMBER)
        deny = button(appContext.getString(R.string.assistant_deny), INDIGO)
        approve.visibility = View.GONE
        deny.visibility = View.GONE
        grant = button(appContext.getString(R.string.phone_access_grant), AMBER)
        grant.visibility = View.GONE
        val send = button(appContext.getString(R.string.assistant_send), INDIGO)
        val read = button(appContext.getString(R.string.assistant_read_screen), INDIGO)
        listOf(input, screenSwitch, read, screenText, send, status, approve, deny, grant).forEach {
            root.addView(it, wrap())
        }
        wire(send, read)
        return root
    }

    private fun wire(send: Button, read: Button) {
        send.setOnClickListener { sendLine() }
        read.setOnClickListener { readScreen() }
        approve.setOnClickListener {
            pendingRequestId?.let { requestId -> uiScope.launch { runner.answer(requestId, approved = true) } }
        }
        deny.setOnClickListener {
            pendingRequestId?.let { requestId -> uiScope.launch { runner.answer(requestId, approved = false) } }
        }
        grant.setOnClickListener {
            val permission = MissingPermission.permissionIn(status.text.toString()) ?: return@setOnClickListener
            val intent = Intent(appContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AndroidPhoneAccess.PERMISSION_EXTRA, permission)
            }
            startAssistantActivity(intent)
            hide()
        }
        uiScope.launch {
            privacy.useScreenText.collect { allowed ->
                writingSwitch = true
                screenSwitch.isChecked = allowed
                writingSwitch = false
            }
        }
        screenSwitch.setOnCheckedChangeListener { _, checked ->
            if (!writingSwitch) uiScope.launch { privacy.setUseScreenText(checked) }
        }
    }

    private fun sendLine() {
        if (busy) return
        val typed = input.text.toString().trim()
        val plan = AssistantPlanner.plan(typed) ?: return
        input.setText("")
        when (plan) {
            is AssistantPlan.OpenChat -> openChat(plan.text)
            is AssistantPlan.Tool -> {
                busy = true
                status.text = ""
                uiScope.launch {
                    try {
                        runner.run(typed, plan.command, ::render)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        status.text = failure.message ?: appContext.getString(R.string.assistant_done)
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }

    private fun openChat(text: String) {
        relay.prefill(text)
        val intent = Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startAssistantActivity(intent)
        hide()
    }

    private fun readScreen() {
        uiScope.launch {
            val allowed = privacy.useScreenText.first()
            if (!ScreenTextPolicy.allow(ownerAllows = allowed, requested = true)) {
                status.text = appContext.getString(R.string.assistant_screen_off)
                return@launch
            }
            val text = ScreenTextCollector.collect(AssistScreenNodes.of(structure))
            screenText.text = text.ifBlank { appContext.getString(R.string.assistant_screen_empty) }
        }
    }

    private fun render(state: AgentOrchestratorState) {
        when (state) {
            is AgentOrchestratorState.WaitingForApproval -> {
                pendingRequestId = state.requestId
                status.text = appContext.getString(R.string.assistant_needs_you)
                approve.visibility = View.VISIBLE
                deny.visibility = View.VISIBLE
                grant.visibility = View.GONE
            }
            is AgentOrchestratorState.Completed -> {
                pendingRequestId = null
                approve.visibility = View.GONE
                deny.visibility = View.GONE
                status.text = state.finalResponse.ifBlank { appContext.getString(R.string.assistant_done) }
                grant.visibility = if (MissingPermission.permissionIn(status.text.toString()) == null) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
                busy = false
            }
            is AgentOrchestratorState.Error -> {
                pendingRequestId = null
                approve.visibility = View.GONE
                deny.visibility = View.GONE
                grant.visibility = View.GONE
                status.text = state.message
                busy = false
            }
            else -> Unit
        }
    }

    private fun title(text: String): TextView = TextView(appContext).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = TITLE_SP
    }

    private fun body(text: String): TextView = TextView(appContext).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = BODY_SP
    }

    private fun button(label: String, color: String): Button = Button(appContext).apply {
        text = label
        setTextColor(Color.WHITE)
        setBackgroundColor(color.toColorInt())
    }

    private fun wrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(GAP_DP) }

    private fun dp(value: Int): Int = (value * appContext.resources.displayMetrics.density).toInt()

    private companion object {
        const val GROUND: String = "#070A14"
        const val INDIGO: String = "#5457E8"
        const val AMBER: String = "#F2B45A"
        const val MUTED: String = "#A8B0C2"
        const val PADDING_DP: Int = 24
        const val GAP_DP: Int = 12
        const val TITLE_SP: Float = 22f
        const val BODY_SP: Float = 16f
    }
}
