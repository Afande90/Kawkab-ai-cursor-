package app.kawkab.android.domain.fastpath

import app.kawkab.android.domain.decision.DecisionResult
import app.kawkab.android.domain.decision.DecisionTrace
import app.kawkab.android.domain.decision.TypedJudge
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
import app.kawkab.android.domain.voice.VoiceAnswers
import app.kawkab.android.hands.AppChoice
import app.kawkab.android.hands.AppChoiceRules
import app.kawkab.android.hands.ContactAnswer
import app.kawkab.android.hands.ContactBook
import app.kawkab.android.hands.ContactChoice
import app.kawkab.android.hands.ContactLookup
import app.kawkab.android.hands.LocalUndo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
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
    private val contacts: ContactBook? = null,
    private val judge: TypedJudge = TypedJudge.NONE,
    private val apps: AppChoice? = null,
) : ChatTierRouter {
    private val picks: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    override fun command(text: String): PlannedCommand? {
        // Money, secrets and bulk deletes are refused from the words alone, before any model reads them.
        SpokenRefusal.reason(text)?.let { refusal ->
            choice.clear()
            apps?.clear()
            return PlannedCommand(REPLY, mapOf(REPLY_TEXT to refusal))
        }
        // A call or text waiting for a contact pick takes the line first, so "the mubadala" or a
        // chip tap completes it instead of going to free chat (task 23j). A new command replaces it.
        CommandFastPath.parse(text)?.let { parsed ->
            choice.clear()
            apps?.clear()
            return parsed
        }
        return pendingAnswer(text) ?: pendingAppAnswer(text) ?: spokenUndo(text)
            ?: skills.replay(text.trim())?.let { PlannedCommand(it.tool, it.args) }
    }

    /**
     * "Undo" said or typed within 10 seconds of a local action takes it back (task 29). Without an
     * action to undo, the line goes on to the usual paths.
     */
    private fun spokenUndo(text: String): PlannedCommand? {
        val undo = localUndo ?: return null
        if (!VoiceAnswers.isUndo(text) || undo.state.value == null) return null
        val result = undo.undo(System.currentTimeMillis(), confirmation = SAID_UNDO) ?: TOO_LATE
        return PlannedCommand(REPLY, mapOf(REPLY_TEXT to result))
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

    private fun pendingAppAnswer(text: String): PlannedCommand? {
        val pending = apps?.state?.value ?: return null
        if (VoiceAnswers.answer(text) == VoiceAnswers.Answer.NO) {
            apps.clear()
            return PlannedCommand(REPLY, mapOf(REPLY_TEXT to CANCELLED))
        }
        val selected = apps.answer(text)
        if (selected == null) return PlannedCommand(REPLY, mapOf(REPLY_TEXT to AppChoiceRules.question(pending)))
        picks += text
        return PlannedCommand("open_app", mapOf("name" to selected))
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
        val app = withContext(DecisionTrace(sessionId)) { resolveApp(text, command) }
        if (app == null) {
            val question = apps?.state?.value?.let(AppChoiceRules::question) ?: "Which installed app do you mean?"
            save(sessionId, Role.AGENT, question, relayed = true, attachment = null)
            onState(AgentOrchestratorState.Completed(question))
            return
        }
        val resolved = withContext(DecisionTrace(sessionId)) { resolveContact(text, app) }
        if (resolved == null) {
            val question = choice.state.value?.let { pending ->
                ContactChoice.question(
                    pending.args[ContactChoice.SPOKEN].orEmpty(),
                    pending.choices.size,
                    pending.choices.map { it.name },
                )
            } ?: "Who do you mean?"
            save(sessionId, Role.AGENT, question, relayed = true, attachment = null)
            onState(AgentOrchestratorState.Completed(question))
            return
        }
        val outcome = dispatch(sessionId, resolved.tool, json(resolved.args), onState, FAST_PATH)
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

    private suspend fun resolveApp(text: String, command: PlannedCommand): PlannedCommand? {
        val resolver = apps ?: return command
        if (command.tool != "open_app") return command
        val selected = resolver.resolve(command.args["name"].orEmpty(), text) ?: return null
        return command.copy(args = command.args + ("name" to selected))
    }

    private suspend fun resolveContact(text: String, command: PlannedCommand): PlannedCommand? {
        val book = contacts
        if (book == null || command.tool !in setOf("call_phone", "send_sms", "send_message")) return command
        val spoken = command.args[ContactChoice.SPOKEN] ?: command.args["contact"].orEmpty()
        var candidates = emptyList<app.kawkab.android.hands.ContactMatch>()
        val number = choice.remembered(spoken)?.also { choice.lastResolved = it }?.phone
            ?: ContactLookup.numberOrNull(
                spoken,
                book,
                choice.lastResolved,
                onChoices = { candidates = it },
                onResolved = { choice.lastResolved = it },
                failure = {},
            )
        if (number != null) {
            return command.copy(
                args = command.args + mapOf(
                    "contact" to number,
                    "spoken" to spoken,
                    "number" to number,
                    "name" to (choice.lastResolved?.name ?: spoken),
                ),
            )
        }
        if (candidates.isEmpty()) return command
        val options = candidates.mapIndexed { i, c -> "${i + 1}: ${c.name} ${c.label}" }
        val decision = judge.choose(text, "Which contact do you mean?", options)
        val picked = (decision as? DecisionResult.Chosen)?.let { options.indexOf(it.option) }
            ?.takeIf { it >= 0 }?.let { candidates[it] }
        if (picked != null) {
            return command.copy(
                args = command.args + mapOf(
                    "contact" to picked.phone,
                    "number" to picked.phone,
                    "spoken" to spoken,
                    "name" to picked.name,
                ),
            )
        }
        choice.offer(
            command.tool,
            command.args + (ContactChoice.SPOKEN to spoken),
            candidates,
            System.currentTimeMillis(),
        )
        return null
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
        const val PICK_AGAIN: String = "Say the number or the name, or say cancel."
        const val SAID_UNDO: String = "said or typed undo"
        const val TOO_LATE: String = "Too late to undo; it has been more than 10 seconds."
        const val SKILL: String = "SKILL"
        const val SEND_SMS: String = "send_sms"
        const val ADD_EVENT: String = "add_calendar_event"
        const val RECENT_MESSAGES: Int = 8
        const val TITLE_MAX: Int = 80
        const val HOUR_MS: Long = 3_600_000L
    }
}
