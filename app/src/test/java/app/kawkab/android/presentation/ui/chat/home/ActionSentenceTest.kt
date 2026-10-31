package app.kawkab.android.presentation.ui.chat.home

import app.kawkab.android.R
import app.kawkab.android.domain.models.ToolRisk
import app.kawkab.android.presentation.ui.common.UiText
import app.kawkab.design.components.chat.ChatContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Task 23i: a confirmation card reads as a sentence, with the raw arguments behind Details. */
class ActionSentenceTest {

    @Test
    fun `a call and a message read as sentences`() {
        assertEquals(
            UiText.Resource(R.string.action_card_call, listOf("Ahmad")),
            ActionSentence.of("call_phone", ActionSentence.arguments("{\"contact\":\"Ahmad\"}")),
        )
        assertEquals(
            UiText.Resource(R.string.action_card_send, listOf("Omar", "I'm late")),
            ActionSentence.of("send_sms", ActionSentence.arguments("{\"contact\":\"Omar\",\"body\":\"I'm late\"}")),
        )
        assertNull(ActionSentence.of("write_file", emptyMap()))
        assertEquals(emptyMap<String, String>(), ActionSentence.arguments("not json"))
    }

    @Test
    fun `the primary button is Send or Call, and Change gives back an editable command`() {
        assertEquals(UiText.Resource(R.string.action_card_primary_send), ActionSentence.primary("send_sms"))
        assertEquals(UiText.Resource(R.string.action_card_primary_call), ActionSentence.primary("call_phone"))
        assertNull(ActionSentence.primary("delete_file"))
        assertEquals("call Ahmad", ActionSentence.editableCommand("call_phone", mapOf("contact" to "Ahmad")))
        assertEquals(
            "text Omar I'm late",
            ActionSentence.editableCommand(
                "send_sms",
                mapOf(
                    "contact" to "Omar",
                    "body" to "I'm late",
                ),
            ),
        )
    }

    @Test
    fun `the live card carries the sentence, the label and Change, and keeps the raw arguments`() {
        var changed = 0
        val row = liveHitlRow(
            modelName = "Gemma",
            pending = HitlPending("call_phone", "{\"contact\":\"Ahmad\"}", ToolRisk.OUTBOUND, requestId = "r1"),
            resolveText = { text -> (text as UiText.Resource).args.joinToString("|", prefix = "${text.id}:") },
            onChange = { changed += 1 },
        )
        val model = (row.content as ChatContent.Confirmation).model
        assertEquals("${R.string.action_card_call}:Ahmad", model.plainSentence)
        assertEquals("${R.string.action_card_primary_call}:", model.primaryLabel)
        assertEquals("\"Ahmad\"", model.arguments["contact"])
        assertNotNull(model.onChange)
        model.onChange!!.invoke()
        assertEquals(1, changed)
    }

    @Test
    fun `a tool without a sentence keeps the developer card`() {
        val row =
            liveHitlRow("Gemma", HitlPending("write_file", "{}", ToolRisk.SENSITIVE, requestId = "r2"), { "x" }, {})
        val model = (row.content as ChatContent.Confirmation).model
        assertNull(model.plainSentence)
        assertNull(model.onChange)
    }
}
