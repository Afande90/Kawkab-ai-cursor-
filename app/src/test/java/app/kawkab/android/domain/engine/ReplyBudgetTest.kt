package app.kawkab.android.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins which prompt tails count as a tool or JSON turn.
 */
class ReplyBudgetTest {

    @Test
    fun `given a chat prompt when classified then it is simple`() {
        assertEquals(ReplyBudget.Kind.SIMPLE, ReplyBudget.kindOf("Hello"))
    }

    @Test
    fun `given a prompt that ends by asking for JSON when classified then it is capped`() {
        val prompt = "x".repeat(ReplyBudget.TAIL_CHARS) + "\nJSON OUTPUT: "
        assertEquals(ReplyBudget.Kind.TOOL_OR_JSON, ReplyBudget.kindOf(prompt))
    }

    @Test
    fun `given a prompt that forbids JSON when classified then it stays simple`() {
        assertEquals(
            ReplyBudget.Kind.SIMPLE,
            ReplyBudget.kindOf("Answer in one sentence and no JSON."),
        )
    }

    @Test
    fun `given a JSON instruction earlier than the tail when classified then the chat stays simple`() {
        val prompt = "JSON OUTPUT: " + "y".repeat(ReplyBudget.TAIL_CHARS)
        assertEquals(ReplyBudget.Kind.SIMPLE, ReplyBudget.kindOf(prompt))
    }
}
