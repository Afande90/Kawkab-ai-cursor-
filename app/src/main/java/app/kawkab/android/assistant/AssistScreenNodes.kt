package app.kawkab.android.assistant

import android.app.assist.AssistStructure
import app.kawkab.android.domain.assistant.ScreenNode

/**
 * Reads an assistant-API screen into the plain screen tree.
 */
internal object AssistScreenNodes {
    /**
     * The windows of [structure], or an empty list when the system sent none.
     */
    fun of(structure: AssistStructure?): List<ScreenNode> {
        if (structure == null) return emptyList()
        return List(structure.windowNodeCount) { index ->
            structure.getWindowNodeAt(index).rootViewNode.toScreenNode()
        }
    }

    private fun AssistStructure.ViewNode.toScreenNode(): ScreenNode {
        val visible = text?.toString()?.trim().orEmpty().ifEmpty {
            contentDescription?.toString()?.trim().orEmpty()
        }
        val nested = List(childCount) { index -> getChildAt(index).toScreenNode() }
        return ScreenNode(text = visible.ifEmpty { null }, children = nested)
    }
}
