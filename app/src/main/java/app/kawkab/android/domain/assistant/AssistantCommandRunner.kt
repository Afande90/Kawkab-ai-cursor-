package app.kawkab.android.domain.assistant

import app.kawkab.android.domain.engine.executors.ToolInvocationGate
import app.kawkab.android.domain.fastpath.ChatTierRouter
import app.kawkab.android.domain.fastpath.PlannedCommand
import app.kawkab.android.domain.models.AgentOrchestratorState
import app.kawkab.android.domain.models.ChatSession
import app.kawkab.android.domain.repositories.ChatRepository
import javax.inject.Inject

/**
 * Runs a panel command through the same tap gate as chat.
 *
 * @param router The tier router. A match goes through it, so the gate still asks.
 * @param chats Where the assistant chat row is stored, so the tap has a session.
 * @param gate The singleton gate. The panel's approve and deny buttons settle it.
 */
class AssistantCommandRunner @Inject constructor(
    private val router: ChatTierRouter,
    private val chats: ChatRepository,
    private val gate: ToolInvocationGate,
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
    fun answer(requestId: String, approved: Boolean): Boolean = gate.resumeWithApproval(SESSION_ID, requestId, approved)

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
