package app.kawkab.android.domain.assistant

import app.kawkab.android.domain.fastpath.CommandFastPath
import app.kawkab.android.domain.fastpath.PlannedCommand

/**
 * What the assistant panel should do with one typed line.
 */
sealed interface AssistantPlan {
    /**
     * A grammar match. The panel runs it through the tap gate.
     *
     * @property command The parsed hands command.
     */
    data class Tool(val command: PlannedCommand) : AssistantPlan

    /**
     * A line the grammar does not know. The main chat takes it.
     *
     * @property text The trimmed line.
     */
    data class OpenChat(val text: String) : AssistantPlan
}

/**
 * Splits a typed assistant line into a hands command or a chat handoff.
 */
object AssistantPlanner {
    /**
     * Plans [raw].
     *
     * @param raw The line typed into the panel.
     * @return Null when the line is blank.
     */
    fun plan(raw: String): AssistantPlan? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val command = CommandFastPath.parse(text)
        return if (command == null) AssistantPlan.OpenChat(text) else AssistantPlan.Tool(command)
    }
}
