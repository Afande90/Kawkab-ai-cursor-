package app.kawkab.android.domain.models

/**
 * Per-tool risk classification used by the Human-in-the-Loop (HITL) gate to decide
 * whether a tool invocation requires explicit user confirmation.
 *
 * The classification is **policy, not capability**: it expresses the assumed
 * side-effect profile of the tool, not what the tool literally does. The single
 * source of truth for resolving a given tool name to a [ToolRisk] is
 * `ToolRepository.getRisk` (built-in defaults + per-AppFunction overrides + MCP
 * blanket policy). Which tiers stop for an approval is decided by
 * [ToolApprovalPolicy.requiresApproval].
 */
enum class ToolRisk {
    /**
     * Pure read-only tool with no externally observable side effects (no writes,
     * no network mutations, no user-data changes). Runs without a prompt unless
     * the user chose [ToolApprovalPolicy.AllCalls] or the node always confirms.
     */
    READ_ONLY,

    /**
     * Tool whose effects are reversible or limited in scope but still
     * user-observable (e.g. scheduling a background task, delegating to a cloud
     * model, writing to local app state). Prompts under the default policy;
     * [ToolApprovalPolicy.NeverPrompt] lets it run without asking.
     */
    SENSITIVE,

    /**
     * Tool whose effects are hard or impossible to reverse (local irreversible
     * edits that are not an outbound action). Prompts under every
     * [ToolApprovalPolicy]; the one control that removes the prompt is the
     * destructive block, and it refuses the call instead of running it.
     */
    DESTRUCTIVE,

    /**
     * Above [DESTRUCTIVE]. Sending, calling, posting, sharing, paying, submitting,
     * or deleting the owner's data. Always shows a confirmation card that needs
     * a tap. No approval policy, per-tool override, or MCP override can quiet it,
     * and the destructive block does not replace the card with a refusal.
     */
    OUTBOUND,
}

/**
 * A classified risk ignores a quieter override. Only [ToolRisk.OUTBOUND] has
 * that floor: a user setting must not let an outbound call run unasked.
 */
fun ToolRisk.notQuietedBy(override: ToolRisk?): ToolRisk =
    if (this == ToolRisk.OUTBOUND) ToolRisk.OUTBOUND else override ?: this

/**
 * One built-in tool in the classification table.
 *
 * @property name Tool name, the same string the executor registers under.
 * @property risk Strictest tier this tool can take. A `GET` on `http_request` is still sensitive.
 * @property outboundCapable True when the tool can send, post, pay, share, or delete the owner's data.
 */
data class RegisteredTool(val name: String, val risk: ToolRisk, val outboundCapable: Boolean)

/**
 * Built-in tools and the tier the approval gate uses.
 * `safety/tool_classification.csv` is the committed copy the CI test checks against [all].
 */
object RegisteredTools {
    /** Every built-in, in a stable order. */
    val all: List<RegisteredTool> = listOf(
        RegisteredTool("schedule_task", ToolRisk.SENSITIVE, outboundCapable = false),
        RegisteredTool("delegate_task", ToolRisk.SENSITIVE, outboundCapable = false),
        RegisteredTool("search_tool", ToolRisk.READ_ONLY, outboundCapable = false),
        RegisteredTool("read_file", ToolRisk.READ_ONLY, outboundCapable = false),
        RegisteredTool("list_files", ToolRisk.READ_ONLY, outboundCapable = false),
        RegisteredTool("find_files", ToolRisk.READ_ONLY, outboundCapable = false),
        RegisteredTool("write_file", ToolRisk.SENSITIVE, outboundCapable = false),
        RegisteredTool("edit_file", ToolRisk.SENSITIVE, outboundCapable = false),
        RegisteredTool("append_file", ToolRisk.SENSITIVE, outboundCapable = false),
        RegisteredTool("delete_file", ToolRisk.OUTBOUND, outboundCapable = true),
        RegisteredTool("http_request", ToolRisk.OUTBOUND, outboundCapable = true),
        RegisteredTool("add_calendar_event", ToolRisk.SENSITIVE, outboundCapable = false),
        RegisteredTool("call_phone", ToolRisk.OUTBOUND, outboundCapable = true),
        RegisteredTool("detect_habits", ToolRisk.READ_ONLY, outboundCapable = false),
        RegisteredTool("macro_send", ToolRisk.OUTBOUND, outboundCapable = true),
        RegisteredTool("open_app", ToolRisk.SENSITIVE, outboundCapable = false),
        RegisteredTool("read_screen", ToolRisk.READ_ONLY, outboundCapable = false),
        RegisteredTool("record_macro", ToolRisk.SENSITIVE, outboundCapable = false),
        RegisteredTool("send_message", ToolRisk.OUTBOUND, outboundCapable = true),
        RegisteredTool("send_sms", ToolRisk.OUTBOUND, outboundCapable = true),
        RegisteredTool("set_alarm", ToolRisk.SENSITIVE, outboundCapable = false),
        RegisteredTool("set_timer", ToolRisk.SENSITIVE, outboundCapable = false),
        RegisteredTool("submit_tap", ToolRisk.OUTBOUND, outboundCapable = true),
    )

    /** Strictest tier registered for [name]. */
    fun risk(name: String): ToolRisk = all.first { it.name == name }.risk
}
