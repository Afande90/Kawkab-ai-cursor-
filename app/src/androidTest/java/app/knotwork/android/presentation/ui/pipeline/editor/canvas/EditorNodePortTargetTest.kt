package app.knotwork.android.presentation.ui.pipeline.editor.canvas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.presentation.ui.pipeline.editor.core.CanvasTransform
import app.knotwork.android.presentation.ui.pipeline.editor.core.CardMeasure
import app.knotwork.android.presentation.ui.pipeline.editor.core.CoordinatesRef
import app.knotwork.design.components.pipelineeditor.NODE_CARD_INBOUND_DOT_TEST_TAG
import app.knotwork.design.components.pipelineeditor.NODE_CARD_OUTBOUND_DOT_TEST_TAG_PREFIX
import app.knotwork.design.components.pipelineeditor.NodeError
import app.knotwork.design.components.pipelineeditor.NodePortGeometry
import app.knotwork.design.components.pipelineeditor.NodePorts
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import app.knotwork.design.components.pipelineeditor.NodeType as CatalogNodeType

/**
 * Every outbound port of an [EditorNode] has a touch target centred on the dot the card draws,
 * and no two targets overlap — so a drag that starts on a dot starts a connection from that port.
 * The card's measure — what the canvas anchors edges and hit tests to — names the line the dots
 * are drawn on, however tall the card is.
 *
 * The targets used to be aligned to the card's bottom edge, under the port labels, and spaced by
 * a formula of their own: on an Intent Router with four or more classes a press on a dot reached
 * a neighbour's target or none. Edges then started on the shortest card's dot line, short of the
 * dots on a card with a body line, an error or a larger font scale.
 */
@RunWith(AndroidJUnit4::class)
class EditorNodePortTargetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** One node to draw: its type and ports, an optional body line or error, and the font scale. */
    private data class Case(
        val name: String,
        val type: NodeType,
        val ports: NodePorts,
        val subtitle: String? = null,
        val error: NodeError? = null,
        val fontScale: Float = 1f,
    )

    private var shown by mutableStateOf(Case("LITE_RT", NodeType.LITE_RT, single()))

    /** The last measure EditorNode reported for its card. */
    private var measured: CardMeasure? = null

    @Test
    fun everyPortTarget_isCentredOnItsDot_andNeighboursNeverOverlap() {
        render()
        assertTargetsOnDots()

        listOf(
            Case("INPUT", NodeType.INPUT, NodePorts.forType(CatalogNodeType.INPUT)),
            Case("IF_CONDITION", NodeType.IF_CONDITION, twoWay()),
        ).forEach { case ->
            shown = case
            assertTargetsOnDots()
        }
        listOf(3, 4, 5, 6).forEach { count ->
            shown = Case("INTENT_ROUTER x$count", NodeType.INTENT_ROUTER, router(count))
            assertTargetsOnDots()
        }
    }

    @Test
    fun aTallerCard_reportsTheLineItsDotsAreDrawnOn() {
        render()
        listOf(
            Case("PIPELINE with a body line", NodeType.PIPELINE, single(), subtitle = "→ research"),
            Case("IF with a runtime error", NodeType.IF_CONDITION, twoWay(), error = NodeError.Runtime("timed out")),
            Case("LITE_RT at 200 %", NodeType.LITE_RT, single(), fontScale = 2f),
            Case("IF at 200 %", NodeType.IF_CONDITION, twoWay(), fontScale = 2f),
            Case("router x5 at 200 % with a body line", NodeType.INTENT_ROUTER, router(5), "5 routes", fontScale = 2f),
        ).forEach { case ->
            shown = case
            assertTargetsOnDots()
        }
    }

    private fun single() = NodePorts.forType(CatalogNodeType.LITE_RT)

    private fun twoWay() = NodePorts.forType(CatalogNodeType.IF_CONDITION)

    private fun router(count: Int) =
        NodePorts.forType(CatalogNodeType.INTENT_ROUTER, intentClasses = CLASS_NAMES.take(count))

    private fun render() {
        composeTestRule.setContent {
            val case = shown
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, case.fontScale)) {
                KnotworkTheme(darkTheme = false) {
                    EditorNode(
                        node = NodeModel(id = "node", type = case.type, x = 0f, y = 0f, label = "Node"),
                        transform = CanvasTransform(),
                        selected = false,
                        multiSelected = false,
                        error = case.error,
                        ports = case.ports,
                        reducedMotion = true,
                        subtitle = case.subtitle,
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
                        onCardMeasured = { measured = it },
                    )
                }
            }
        }
    }

    private fun assertTargetsOnDots() {
        composeTestRule.waitForIdle()
        val case = shown
        val count = case.ports.outbound.size
        // The inbound dot sits at the top of the card's layout box: the top the measure counts from.
        val cardTop = if (case.ports.inbound > 0) bounds(NODE_CARD_INBOUND_DOT_TEST_TAG).top.value else null
        val measure = measured
        val targets = (0 until count).map { index ->
            val dot = bounds(NODE_CARD_OUTBOUND_DOT_TEST_TAG_PREFIX + index)
            val target = bounds(EDITOR_PORT_TARGET_TEST_TAG_PREFIX + index)
            val name = "${case.name} #$index"
            assertEquals("$name: target width", NodePortGeometry.HIT_TARGET, target.width.value, TOLERANCE)
            assertEquals("$name: target height", NodePortGeometry.HIT_TARGET, target.height.value, TOLERANCE)
            assertEquals("$name: centre x", dot.centreX(), target.centreX(), TOLERANCE)
            assertEquals("$name: centre y", dot.centreY(), target.centreY(), TOLERANCE)
            if (cardTop != null) {
                assertNotNull("${case.name}: no card measure reported", measure)
                assertEquals("$name: measured dot line", dot.centreY() - cardTop, measure!!.outboundDotLine, TOLERANCE)
            }
            target
        }
        targets.zipWithNext().forEachIndexed { index, (left, right) ->
            assertTrue(
                "${case.name}: target #$index ends at ${left.right}, #${index + 1} starts at ${right.left}",
                left.right.value <= right.left.value + TOLERANCE,
            )
        }
    }

    // EditorNode's click handler merges its descendants' semantics, so the dots and targets
    // are found in the unmerged tree.
    private fun bounds(tag: String): DpRect =
        composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    private fun DpRect.centreX(): Float = (left.value + right.value) / 2f

    private fun DpRect.centreY(): Float = (top.value + bottom.value) / 2f

    private companion object {
        /** One dp: a device density that is not a whole number rounds every edge to a pixel. */
        const val TOLERANCE = 1f

        val CLASS_NAMES = listOf("billing", "support", "sales", "refund", "general", "other")
    }
}
