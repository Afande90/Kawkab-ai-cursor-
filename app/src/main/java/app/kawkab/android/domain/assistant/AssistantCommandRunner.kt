package app.kawkab.android.domain.assistant

import app.kawkab.android.domain.fastpath.ChatTierRouter
import app.kawkab.android.domain.fastpath.PlannedCommand
import app.kawkab.android.domain.models.AgentOrchestratorState
import app.kawkab.android.domain.models.ChatSession
import app.kawkab.android.domain.repositories.ChatRepository
import app.kawkab.android.domain.safety.ActivityLog
import app.kawkab.android.domain.safety.ActivityLogEntry
import app.kawkab.android.domain.usecases.PendingSubmissionOutcome
import app.kawkab.android.domain.usecases.SubmitApprovalDecisionUseCase
import javax.inject.Inject

/**
 * Runs a panel command through the same tap gate as chat.
 *
 * @param router The tier router. A match goes through it, so the gate still asks.
 * @param chats Where the assistant chat row is stored, so the tap has a session.
 * @param approvals The only way a tap settles a waiting tool. The panel does not call the gate itself.
 * @param log The activity log, which records a card answered by voice (task 29, spec §0.4.6).
 */
class AssistantCommandRunner @Inject constructor(
    private val router: ChatTierRouter,
    private val chats: ChatRepository,
    private val approvals: SubmitApprovalDecisionUseCase,
    private val log: ActivityLog,
) {
    /**
     * Runs [command] for [text] and forwards each orchestrator state.
     *
     * @param text The line the owner typed.
     * @param command The grammar match for [text].
     * @param onState Receives waiting, completed, and error states.
     */
    suspend fun run(text: String, command: PlannedCommand, onState: (AgentOrchestratorState) -> Unit) {
        if (chats.getSessionById(SESSION_ID) == null) {
            chats.saveSession(
                ChatSession(
                    id = SESSION_ID,
                    name = SESSION_NAME,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        router.runCommand(SESSION_ID, text, command, onState)
    }

    /**
     * Settles the tap the panel is showing.
     *
     * @param requestId The gate's request id.
     * @param approved Whether the owner tapped approve.
     * @return Whether that request was still waiting.
     */
    suspend fun answer(requestId: String, approved: Boolean): Boolean =
        approvals(SESSION_ID, requestId, approved) == PendingSubmissionOutcome.LiveResumed

    /**
     * Settles the card from a spoken answer that `VoiceAnswers.decide` allowed, and records that
     * the confirmation was spoken, not tapped (task 29).
     *
     * @param requestId The gate's request id.
     * @param approved Whether the answer was yes.
     * @param heard What the recognizer heard.
     * @param tool The tool the card was for.
     * @return Whether that request was still waiting.
     */
    suspend fun answerByVoice(requestId: String, approved: Boolean, heard: String, tool: String): Boolean {
        val settled = answer(requestId, approved)
        log.record(
            ActivityLogEntry(
                asked = heard,
                ran = tool,
                entryPoint = "voice",
                result = if (approved) "approved" else "denied",
                confirmation = if (approved) "voice, screen on and unlocked" else "voice no",
                atEpochMs = System.currentTimeMillis(),
            ),
        )
        return settled
    }

    /**
     * Ids of the assistant chat.
     */
    companion object {
        /**
         * Stable session id for commands started from the panel.
         */
        const val SESSION_ID: String = "kawkab-assistant"

        /**
         * Display name of that chat.
         */
        const val SESSION_NAME: String = "Kawkab"
    }
}
