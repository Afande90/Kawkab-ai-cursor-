package app.kawkab.android.domain.assistant

/**
 * One node of on-screen text, with the nodes drawn inside it.
 *
 * @property text Visible text, or null when the node has none.
 * @property children Nodes contained in this one, in reading order.
 */
data class ScreenNode(val text: String?, val children: List<ScreenNode> = emptyList())

/**
 * Joins the visible text of a screen tree into one block.
 */
object ScreenTextCollector {
    /**
     * Collects trimmed text from [roots], one node per line, skipping blanks.
     *
     * @param roots The top-level windows or views.
     * @return The joined text, or an empty string when nothing was visible.
     */
    fun collect(roots: List<ScreenNode>): String {
        val parts = mutableListOf<String>()
        fun walk(node: ScreenNode) {
            val visible = node.text?.trim().orEmpty()
            if (visible.isNotEmpty()) parts += visible
            node.children.forEach(::walk)
        }
        roots.forEach(::walk)
        return parts.joinToString(separator = "\n")
    }
}
