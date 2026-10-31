package app.kawkab.android.domain.fastpath

import app.kawkab.android.domain.engine.LlmInferenceEngine
import app.kawkab.android.domain.engine.executors.ToolInvocationGate
import app.kawkab.android.domain.harness.SkillCache
import app.kawkab.android.domain.models.AgentOrchestratorState
import app.kawkab.android.domain.models.ChatMessage
import app.kawkab.android.domain.models.MessageAttachment
import app.kawkab.android.domain.models.NodeExecutionResult
import app.kawkab.android.domain.models.NodeOutput
import app.kawkab.android.domain.models.RegisteredTools
import app.kawkab.android.domain.models.Role
import app.kawkab.android.domain.models.ToolRisk
import app.kawkab.android.domain.prompt.ChatTranscript
import app.kawkab.android.domain.repositories.ChatRepository
import app.kawkab.android.hands.ContactAnswer
import app.kawkab.android.hands.ContactChoice
import app.kawkab.android.hands.LocalUndo
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
    private val skills: SkillCache = SkillCache(),
    private val choice: ContactChoice = ContactChoice(),
    private val localUndo: LocalUndo? = null,
) : ChatTierRouter {
    private val picks: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    override fun command(text: String): PlannedCommand? {
        // A call or text waiting for a contact pick takes the line first, so "the mubadala" or a
        // chip tap completes it instead of going to free chat (task 23j). A new command replaces it.
        CommandFastPath.parse(text)?.let { parsed ->
            choice.clear()
            return parsed
        }
        pendingAnswer(text)?.let { return it }
        val saved = skills.replay(text.trim()) ?: return null
        return PlannedCommand(saved.tool, saved.args)
    }

    /** The command a line completes while a contact choice is pending, or null when none is. */
    private fun pendingAnswer(text: String): PlannedCommand? =
        when (val answer = choice.answer(text, System.currentTimeMillis())) {
            is ContactAnswer.Picked -> {
                picks += text
                PlannedCommand(answer.tool, answer.args)
            }
            ContactAnswer.Cancelled -> PlannedCommand(REPLY, mapOf(REPLY_TEXT to CANCELLED))
            ContactAnswer.Unclear -> PlannedCommand(REPLY, mapOf(REPLY_TEXT to PICK_AGAIN))
            ContactAnswer.NotPending -> null
        }

    override fun recipe(text: String, hasImage: Boolean): TierTwoRecipe? = TierTwoRecipes.match(text, hasImage)

    override suspend fun runCommand(
        sessionId: String,
        text: String,
        command: PlannedCommand,
        onState: (AgentOrchestratorState) -> Unit,
    ) {
        save(sessionId, Role.USER, text, relayed = false, attachment = null)
        if (command.tool == REPLY) {
            val reply = command.args[REPLY_TEXT].orEmpty()
            save(sessionId, Role.AGENT, reply, relayed = true, attachment = null)
            onState(AgentOrchestratorState.Completed(reply))
            return
        }
        val outcome = dispatch(sessionId, command.tool, json(command.args), onState, FAST_PATH)
        // A local action ran without a card; say so and offer Undo for 10 seconds (task 23i).
        val local = RegisteredTools.all.any { it.name == command.tool && it.risk == ToolRisk.LOCAL }
        finish(sessionId, outcome, onState, relayed = true, note = LocalUndo.NO_ASK_NOTE.takeIf { local })
        if (local && outcome != null && outcome.error == null) {
            localUndo?.offer(command.tool, command.args, System.currentTimeMillis())
        }
        // A contact pick ("the second one") is not a command to replay later.
        val wasPick = picks.remove(text)
        if (outcome != null && outcome.error == null && !wasPick) {
            skills.remember(text, command.tool, command.args)
        }
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
        note: String? = null,
    ) {
        val failure = outcome?.error
        if (outcome == null || failure != null) {
            onState(AgentOrchestratorState.Error(failure ?: "The action did not finish."))
            return
        }
        val done = outcome.outputText?.takeIf { it.isNotBlank() } ?: "Done."
        val text = if (note == null) done else "$done $note"
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
        .joinToString(separator = "\n") { ChatTranscript.turn(it.role.name, it.content) }

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

        /** A reply with no tool, for answers to a pending contact choice. */
        const val REPLY: String = "kawkab_reply"
        const val REPLY_TEXT: String = "text"
        const val CANCELLED: String = "Cancelled."
        const val PICK_AGAIN: String = "Tap one of the names below, or say cancel."
        const val SKILL: String = "SKILL"
        const val SEND_SMS: String = "send_sms"
        const val ADD_EVENT: String = "add_calendar_event"
        const val RECENT_MESSAGES: Int = 8
        const val TITLE_MAX: Int = 80
        const val HOUR_MS: Long = 3_600_000L
    }
}
