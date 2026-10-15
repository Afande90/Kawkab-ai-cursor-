package app.knotwork.android.presentation.ui.pipeline.editor.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.presentation.ui.pipeline.editor.core.CanvasTransform
import app.knotwork.android.presentation.ui.pipeline.editor.core.CoordinatesRef
import app.knotwork.design.components.pipelineeditor.NODE_CARD_OUTBOUND_DOT_TEST_TAG_PREFIX
import app.knotwork.design.components.pipelineeditor.NodePortGeometry
import app.knotwork.design.components.pipelineeditor.NodePorts
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import app.knotwork.design.components.pipelineeditor.NodeType as CatalogNodeType

/**
 * Every outbound port of an [EditorNode] has a touch target centred on the dot the card draws,
 * and no two targets overlap — so a drag that starts on a dot starts a connection from that port.
 *
 * The targets used to be aligned to the card's bottom edge, under the port labels, and spaced by
 * a formula of their own: on an Intent Router with four or more classes a press on a dot reached
 * a neighbour's target or none.
 */
@RunWith(AndroidJUnit4::class)
class EditorNodePortTargetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var shown by mutableStateOf(NodeType.LITE_RT to NodePorts.forType(CatalogNodeType.LITE_RT))

    @Test
    fun everyPortTarget_isCentredOnItsDot_andNeighboursNeverOverlap() {
        composeTestRule.setContent {
            KnotworkTheme(darkTheme = false) {
                EditorNode(
                    node = NodeModel(id = "node", type = shown.first, x = 0f, y = 0f, label = "Node"),
                    transform = CanvasTransform(),
                    selected = false,
                    multiSelected = false,
                    error = null,
                    ports = shown.second,
                    reducedMotion = true,
                    onSelect = {},
                    onOpenConfig = {},
                    onLongPress = {},
                    onDrag = { _, _ -> },
                    onDragEnd = {},
                    onConnectionStart = { _, _, _ -> },
                    onConnectionMove = { _, _ -> },
                    onConnectionEnd = {},
                    onConnectionCancel = {},
                    canvasLayoutCoordinatesRef = CoordinatesRef(),
                )
            }
        }
        assertTargetsOnDots("LITE_RT")

        shown = NodeType.INPUT to NodePorts.forType(CatalogNodeType.INPUT)
        assertTargetsOnDots("INPUT")

        shown = NodeType.IF_CONDITION to NodePorts.forType(CatalogNodeType.IF_CONDITION)
        assertTargetsOnDots("IF_CONDITION")

        listOf(3, 4, 5, 6).forEach { count ->
            shown = NodeType.INTENT_ROUTER to
                NodePorts.forType(CatalogNodeType.INTENT_ROUTER, intentClasses = CLASS_NAMES.take(count))
            assertTargetsOnDots("INTENT_ROUTER x$count")
        }
    }

    private fun assertTargetsOnDots(case: String) {
        composeTestRule.waitForIdle()
        val count = shown.second.outbound.size
        val targets = (0 until count).map { index ->
            val dot = bounds(NODE_CARD_OUTBOUND_DOT_TEST_TAG_PREFIX + index)
            val target = bounds(EDITOR_PORT_TARGET_TEST_TAG_PREFIX + index)
            assertEquals("$case #$index: target width", NodePortGeometry.HIT_TARGET, target.width.value, TOLERANCE)
            assertEquals("$case #$index: target height", NodePortGeometry.HIT_TARGET, target.height.value, TOLERANCE)
            assertEquals("$case #$index: centre x", dot.centreX(), target.centreX(), TOLERANCE)
            assertEquals("$case #$index: centre y", dot.centreY(), target.centreY(), TOLERANCE)
            target
        }
        targets.zipWithNext().forEachIndexed { index, (left, right) ->
            assertTrue(
                "$case: target #$index ends at ${left.right}, #${index + 1} starts at ${right.left}",
                left.right.value <= right.left.value + TOLERANCE,
            )
        }
    }

    private fun bounds(tag: String): DpRect = composeTestRule.onNodeWithTag(tag).getBoundsInRoot()

    private fun DpRect.centreX(): Float = (left.value + right.value) / 2f

    private fun DpRect.centreY(): Float = (top.value + bottom.value) / 2f

    private companion object {
        /** One dp: a device density that is not a whole number rounds every edge to a pixel. */
        const val TOLERANCE = 1f

        val CLASS_NAMES = listOf("billing", "support", "sales", "refund", "general", "other")
    }
}
