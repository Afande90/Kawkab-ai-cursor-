package app.kawkab.android.data.tools.local

import app.kawkab.android.data.tools.local.executors.AddCalendarEventExecutor
import app.kawkab.android.data.tools.local.executors.CallPhoneExecutor
import app.kawkab.android.data.tools.local.executors.DetectHabitsExecutor
import app.kawkab.android.data.tools.local.executors.MacroSendExecutor
import app.kawkab.android.data.tools.local.executors.OpenAppExecutor
import app.kawkab.android.data.tools.local.executors.ReadScreenExecutor
import app.kawkab.android.data.tools.local.executors.RecordMacroExecutor
import app.kawkab.android.data.tools.local.executors.SendMessageExecutor
import app.kawkab.android.data.tools.local.executors.SendSmsExecutor
import app.kawkab.android.data.tools.local.executors.SetAlarmExecutor
import app.kawkab.android.data.tools.local.executors.SetTimerExecutor
import app.kawkab.android.data.tools.local.executors.SubmitTapExecutor
import app.kawkab.android.domain.models.AgentTool
import app.kawkab.android.domain.models.RegisteredTools

/**
 * The hands tools published next to the original built-ins. Risk comes from [RegisteredTools].
 */
fun handAgentTools(): List<AgentTool> = listOf(
    hand(CallPhoneExecutor.TOOL_NAME, CallPhoneExecutor.DESCRIPTION, CallPhoneExecutor.PARAMETERS),
    hand(SendSmsExecutor.TOOL_NAME, SendSmsExecutor.DESCRIPTION, SendSmsExecutor.PARAMETERS),
    hand(SendMessageExecutor.TOOL_NAME, SendMessageExecutor.DESCRIPTION, SendMessageExecutor.PARAMETERS),
    hand(SetAlarmExecutor.TOOL_NAME, SetAlarmExecutor.DESCRIPTION, SetAlarmExecutor.PARAMETERS),
    hand(SetTimerExecutor.TOOL_NAME, SetTimerExecutor.DESCRIPTION, SetTimerExecutor.PARAMETERS),
    hand(OpenAppExecutor.TOOL_NAME, OpenAppExecutor.DESCRIPTION, OpenAppExecutor.PARAMETERS),
    hand(AddCalendarEventExecutor.TOOL_NAME, AddCalendarEventExecutor.DESCRIPTION, AddCalendarEventExecutor.PARAMETERS),
    hand(ReadScreenExecutor.TOOL_NAME, ReadScreenExecutor.DESCRIPTION, ReadScreenExecutor.PARAMETERS),
    hand(SubmitTapExecutor.TOOL_NAME, SubmitTapExecutor.DESCRIPTION, SubmitTapExecutor.PARAMETERS),
    hand(RecordMacroExecutor.TOOL_NAME, RecordMacroExecutor.DESCRIPTION, RecordMacroExecutor.PARAMETERS),
    hand(MacroSendExecutor.TOOL_NAME, MacroSendExecutor.DESCRIPTION, MacroSendExecutor.PARAMETERS),
    hand(DetectHabitsExecutor.TOOL_NAME, DetectHabitsExecutor.DESCRIPTION, DetectHabitsExecutor.PARAMETERS),
)

private fun hand(name: String, description: String, parameters: String): AgentTool = AgentTool(
    name = name,
    description = description,
    parameters = parameters,
    risk = RegisteredTools.risk(name),
)
