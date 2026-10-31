package app.kawkab.android.domain.engine.executors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 23h: a tool name or JSON printed as text is read as a call, never shown. */
class ToolReplyReaderTest {
    private val known = setOf("set_alarm", "call_phone", "open_app")

    @Test
    fun `Gemma's own call markup`() {
        val reply = "<start_function_call>call:set_alarm{hour:<escape>6<escape>,minute:0}<end_function_call>"
        val call = ToolReplyReader.read(reply, known) as ToolReply.Call
        assertEquals("set_alarm", call.tool)
        assertTrue(call.argumentsJson, call.argumentsJson.contains("\"hour\":6"))
        assertTrue(call.argumentsJson, call.argumentsJson.contains("\"minute\":0"))
    }

    @Test
    fun `fenced and plain JSON`() {
        val fenced = "```json\n{\"tool\": \"set_alarm\", \"arguments\": {\"hour\": 6}}\n```"
        assertEquals(ToolReply.Call("set_alarm", "{\"hour\":6}"), ToolReplyReader.read(fenced, known))
        val plain = "Sure. {\"name\": \"open_app\", \"parameters\": {\"name\": \"Messages\"}}"
        assertEquals(ToolReply.Call("open_app", "{\"name\":\"Messages\"}"), ToolReplyReader.read(plain, known))
    }

    @Test
    fun `a call written as name(args)`() {
        val call = ToolReplyReader.read("set_alarm(hour=6, minute=30)", known) as ToolReply.Call
        assertEquals("set_alarm", call.tool)
        assertTrue(call.argumentsJson.contains("\"minute\":30"))
    }

    @Test
    fun `a bare tool name or a call without arguments is NamedOnly`() {
        assertEquals(ToolReply.NamedOnly("set_alarm"), ToolReplyReader.read("set_alarm", known))
        assertEquals(ToolReply.NamedOnly("set_alarm"), ToolReplyReader.read("`set_alarm`.", known))
        assertEquals(ToolReply.NamedOnly("set_alarm"), ToolReplyReader.read("{\"tool\": \"set_alarm\"}", known))
    }

    @Test
    fun `ordinary text and unknown tools stay text`() {
        assertEquals(ToolReply.Text, ToolReplyReader.read("Alarm set for 6:00 AM.", known))
        assertEquals(
            ToolReply.Text,
            ToolReplyReader.read("{\"tool\": \"delete_everything\", \"arguments\": {}}", known),
        )
        assertEquals(ToolReply.Text, ToolReplyReader.read("", known))
    }

    @Test
    fun `the fallback sentence names a command to try`() {
        assertEquals("set an alarm for 6", ToolReplyReader.example("set_alarm"))
        assertEquals("set an alarm for 6", ToolReplyReader.example("unknown_tool"))
    }
}
