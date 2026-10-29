package app.kawkab.android.domain.fastpath

import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.engine.executors.ToolInvocationGate
import app.kawkab.android.domain.models.AgentOrchestratorState
import app.kawkab.android.domain.models.ChatMessage
import app.kawkab.android.domain.models.MessageAttachment
import app.kawkab.android.domain.models.NodeExecutionResult
import app.kawkab.android.domain.models.NodeOutput
import app.kawkab.android.domain.models.Role
import app.kawkab.android.domain.repositories.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import org.json.JSONObject
import javax.inject.Inject

/**
 * Decides whether a typed line skips the pipeline.
 *
 * Tier 1 is a grammar match and runs with no model. Tier 2 is one of three
 * skills: the model writes only the words, and any send still goes through the
 * tap gate. Everything else returns null and the caller keeps the agent loop.
 */
interface ChatTierRouter {
    /**
     * The hands command in [text], or null when the grammar does not know it.
     *
     * @param text The trimmed composer text.
     * @return The planned command, or null.
     */
    fun command(text: String): PlannedCommand?

    /**
     * The skill in [text], or null when it is not one of the three.
     *
     * @param text The trimmed composer text.
     * @param hasImage Whether the send carries a picture.
     * @return The skill, or null.
     */
    fun recipe(text: String, hasImage: Boolean): TierTwoRecipe?

    /**
     * Runs [command] through the tap gate and reports each state on [onState].
     *
     * Saves the user's line and, when the tool finishes, a relayed answer. The
     * model is not called.
     *
     * @param sessionId Chat the line belongs to.
     * @param text What the user typed.
     * @param command The grammar's plan.
     * @param onState Receives waiting, result, and terminal states.
     */
    suspend fun runCommand(
        sessionId: String,
        text: String,
        command: PlannedCommand,
        onState: (AgentOrchestratorState) -> Unit,
    )

    /**
     * Runs [recipe] and reports each state on [onState].
     *
     * @param sessionId Chat the line belongs to.
     * @param text What the user typed.
     * @param recipe The skill to run.
     * @param imagePath Absolute path of the attached picture, or null.
     * @param attachment The picture to store on the user row, or null.
     * @param onState Receives waiting, result, and terminal states.
     */
    suspend fun runRecipe(
        sessionId: String,
        text: String,
        recipe: TierTwoRecipe,
        imagePath: String?,
        attachment: MessageAttachment?,
        onState: (AgentOrchestratorState) -> Unit,
    )
}

/**
 * Router that claims nothing, so a screen built without one stays on the pipeline.
 */
class PassthroughChatTierRouter : ChatTierRouter {
    override fun command(text: String): PlannedCommand? = null

    override fun recipe(text: String, hasImage: Boolean): TierTwoRecipe? = null

    override suspend fun runCommand(
        sessionId: String,
        text: String,
        command: PlannedCommand,
        onState: (AgentOrchestratorState) -> Unit,
    ) = Unit

    override suspend fun runRecipe(
        sessionId: String,
        text: String,
        recipe: TierTwoRecipe,
        imagePath: String?,
        attachment: MessageAttachment?,
        onState: (AgentOrchestratorState) -> Unit,
    ) = Unit
}

/**
 * Runs a matched command or skill. Tool calls go through [ToolInvocationGate].
 */
class HandsChatTierRouter @Inject constructor(
    private val gate: ToolInvocationGate,
    private val chatRepository: ChatRepository,
    private val engine: LlmInferenceEngine,
) : ChatTierRouter {
    override fun command(text: String): PlannedCommand? = CommandFastPath.parse(text)

    override fun recipe(text: String, hasImage: Boolean): TierTwoRecipe? = TierTwoRecipes.match(text, hasImage)

    override suspend fun runCommand(
        sessionId: String,
        text: String,
        command: PlannedCommand,
        onState: (AgentOrchestratorState) -> Unit,
    ) {
        save(sessionId, Role.USER, text, relayed = false, attachment = null)
        val outcome = dispatch(sessionId, command.tool, json(command.args), onState, FAST_PATH)
        finish(sessionId, outcome, onState, relayed = true)
    }

    override suspend fun runRecipe(
        sessionId: String,
        text: String,
        recipe: TierTwoRecipe,
        imagePath: String?,
        attachment: MessageAttachment?,
        onState: (AgentOrchestratorState) -> Unit,
    ) {
        val history = recent(sessionId)
        save(sessionId, Role.USER, text, relayed = false, attachment = attachment)
        when (recipe) {
            is TierTwoRecipe.Reply -> reply(sessionId, recipe, history, onState)
            TierTwoRecipe.ReadLast -> readLast(sessionId, history, onState)
            TierTwoRecipe.ScreenshotReminder -> reminder(sessionId, imagePath, onState)
        }
    }

    private suspend fun reply(
        sessionId: String,
        recipe: TierTwoRecipe.Reply,
        history: String,
        onState: (AgentOrchestratorState) -> Unit,
    ) {
        val who = recipe.contact.ifBlank { "them" }
        val body = modelText("Write only the short message to send to $who. No preamble.\n$history", imagePath = null)
        if (body.isBlank()) {
            onState(AgentOrchestratorState.Error("No reply was written."))
            return
        }
        if (recipe.contact.isBlank()) {
            save(sessionId, Role.AGENT, body, relayed = false, attachment = null)
            onState(AgentOrchestratorState.Completed(body))
            return
        }
        val outcome = dispatch(
            sessionId,
            SEND_SMS,
            json(mapOf("contact" to recipe.contact, "body" to body)),
            onState,
            SKILL,
        )
        finish(sessionId, outcome, onState, relayed = true)
    }

    private suspend fun readLast(sessionId: String, history: String, onState: (AgentOrchestratorState) -> Unit) {
        val reading = if (history.isBlank()) {
            "There are no messages to read."
        } else {
            modelText(
                "Summarise these messages in one short paragraph, in their language. No preamble.\n$history",
                imagePath = null,
            ).ifBlank { "There are no messages to read." }
        }
        save(sessionId, Role.AGENT, reading, relayed = history.isBlank(), attachment = null)
        onState(AgentOrchestratorState.Completed(reading))
    }

    private suspend fun reminder(sessionId: String, imagePath: String?, onState: (AgentOrchestratorState) -> Unit) {
        if (imagePath.isNullOrBlank()) {
            onState(AgentOrchestratorState.Error("A screenshot is needed."))
            return
        }
        val title = modelText(
            "Write only a short reminder title for this picture. No other words.",
            imagePath,
        ).lineSequence().firstOrNull().orEmpty().trim().trim('"').take(TITLE_MAX)
        if (title.isBlank()) {
            onState(AgentOrchestratorState.Error("No reminder title was written."))
            return
        }
        val start = System.currentTimeMillis() + HOUR_MS
        val outcome = dispatch(
            sessionId,
            ADD_EVENT,
            json(mapOf("title" to title), mapOf("start" to start)),
            onState,
            SKILL,
        )
        finish(sessionId, outcome, onState, relayed = true)
    }

    private suspend fun dispatch(
        sessionId: String,
        tool: String,
        args: String,
        onState: (AgentOrchestratorState) -> Unit,
        nodeType: String,
    ): NodeExecutionResult? {
        var result: NodeExecutionResult? = null
        flow {
            gate.dispatch(
                collector = this,
                nodeType = nodeType,
                nodeId = nodeType,
                sessionId = sessionId,
                runId = null,
                resolvedToolName = tool,
                resolvedToolArgs = args,
                alwaysConfirm = false,
            )
        }.collect { output ->
            when (output) {
                is NodeOutput.State -> onState(output.state)
                is NodeOutput.Result -> result = output.result
                is NodeOutput.Console -> Unit
            }
        }
        return result
    }

    private suspend fun finish(
        sessionId: String,
        outcome: NodeExecutionResult?,
        onState: (AgentOrchestratorState) -> Unit,
        relayed: Boolean,
    ) {
        val failure = outcome?.error
        if (outcome == null || failure != null) {
            onState(AgentOrchestratorState.Error(failure ?: "The action did not finish."))
            return
        }
        val text = outcome.outputText?.takeIf { it.isNotBlank() } ?: "Done."
        save(sessionId, Role.AGENT, text, relayed, attachment = null)
        onState(AgentOrchestratorState.Completed(text))
    }

    private suspend fun modelText(prompt: String, imagePath: String?): String = try {
        engine.generateResponseStream(prompt, imagePath, sampling = null)
            .toList()
            .joinToString(separator = "")
            .trim()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        ""
    }

    private suspend fun recent(sessionId: String): String = chatRepository.getMessagesForSession(sessionId)
        .first()
        .filter { it.isFinal }
        .takeLast(RECENT_MESSAGES)
        .joinToString(separator = "\n") { "${it.role}: ${it.content}" }

    private suspend fun save(
        sessionId: String,
        role: Role,
        content: String,
        relayed: Boolean,
        attachment: MessageAttachment?,
    ) {
        chatRepository.saveMessage(
            ChatMessage(
                sessionId = sessionId,
                role = role,
                content = content,
                timestamp = System.currentTimeMillis(),
                isFinal = true,
                attachment = attachment,
                relayed = relayed,
            ),
        )
    }

    private fun json(text: Map<String, String>, numbers: Map<String, Long> = emptyMap()): String {
        val objectJson = JSONObject()
        text.forEach { (key, value) -> objectJson.put(key, value) }
        numbers.forEach { (key, value) -> objectJson.put(key, value) }
        return objectJson.toString()
    }

    private companion object {
        const val FAST_PATH: String = "FAST_PATH"
        const val SKILL: String = "SKILL"
        const val SEND_SMS: String = "send_sms"
        const val ADD_EVENT: String = "add_calendar_event"
        const val RECENT_MESSAGES: Int = 8
        const val TITLE_MAX: Int = 80
        const val HOUR_MS: Long = 3_600_000L
    }
}
