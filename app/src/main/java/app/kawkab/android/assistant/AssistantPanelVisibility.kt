package app.kawkab.android.assistant

/** Whether the assistant panel is on screen, for the announce-and-act rule (vision rule 1). */
object AssistantPanelVisibility {
    @Volatile
    var shown: Boolean = false
        internal set
}
