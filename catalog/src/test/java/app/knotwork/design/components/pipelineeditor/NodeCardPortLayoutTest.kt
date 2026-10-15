package app.knotwork.design.components.pipelineeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [NodeCard] draws every outbound port where [NodePortGeometry] says it is — the positions the
 * editor canvas starts its edges from and places its touch targets at.
 *
 * The card used to lay its ports out in a row as wide as their labels: with four Intent Router
 * classes the dots drifted from where the canvas expected them, and with five or six two of them
 * were squeezed to nothing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w800dp-h600dp-mdpi")
class NodeCardPortLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var shown by mutableStateOf(NodeType.LITE_RT to NodePorts.forType(NodeType.LITE_RT))

    @Test
    fun `given a single port when drawn then its dot is centred on the card's bottom edge`() {
        render()
        assertDotsWhereTheGeometrySays()
    }

    @Test
    fun `given an input node without an inbound dot when drawn then its outbound dot is on the surface edge`() {
        shown = NodeType.INPUT to NodePorts.forType(NodeType.INPUT)
        render()
        assertDotsWhereTheGeometrySays()
    }

    @Test
    fun `given the two branches of an IF when drawn then each dot is in its own slot`() {
        shown = NodeType.IF_CONDITION to NodePorts.forType(NodeType.IF_CONDITION)
        render()
        assertDotsWhereTheGeometrySays()
    }

    @Test
    fun `given a router with three to eight classes when drawn then every dot is drawn in its slot`() {
        render()
        listOf(3, 4, 5, 6, 8).forEach { count ->
            shown = NodeType.INTENT_ROUTER to
                NodePorts.forType(NodeType.INTENT_ROUTER, intentClasses = CLASS_NAMES.take(count))
            composeTestRule.waitForIdle()
            assertDotsWhereTheGeometrySays()
        }
    }

    private fun render() {
        composeTestRule.setContent {
            KnotworkTheme(darkTheme = false) {
                NodeCard(
                    type = shown.first,
                    title = "Node",
                    subtitle = null,
                    selected = false,
                    error = null,
                    multiSelected = false,
                    ports = shown.second,
                    modifier = Modifier.testTag(CARD),
                )
            }
        }
    }

    private fun assertDotsWhereTheGeometrySays() {
        val ports = shown.second
        val count = ports.outbound.size
        val card = composeTestRule.onNodeWithTag(CARD).getBoundsInRoot()
        assertEquals("$count ports: card width", NodePortGeometry.cardWidth(count), card.width.value, TOLERANCE)

        val centreX = card.left.value + card.width.value / 2f
        if (ports.inbound > 0) {
            val inbound = composeTestRule.onNodeWithTag(NODE_CARD_INBOUND_DOT_TEST_TAG).getBoundsInRoot()
            assertEquals("inbound dot x", centreX, inbound.centreX(), TOLERANCE)
            assertEquals("inbound dot y", card.top.value + NodePortGeometry.INBOUND_DOT_Y, inbound.centreY(), TOLERANCE)
        }
        val dotLine = card.top.value + NodePortGeometry.outboundDotY(hasInbound = ports.inbound > 0)
        (0 until count).forEach { index ->
            val dot = composeTestRule.onNodeWithTag(NODE_CARD_OUTBOUND_DOT_TEST_TAG_PREFIX + index).getBoundsInRoot()
            assertEquals(
                "$count ports, #$index: dot diameter",
                2 * NodePortGeometry.DOT_RADIUS,
                dot.width.value,
                TOLERANCE,
            )
            assertEquals(
                "$count ports, #$index: dot x",
                centreX + NodePortGeometry.outboundOffset(index, count),
                dot.centreX(),
                TOLERANCE,
            )
            assertEquals("$count ports, #$index: dot y", dotLine, dot.centreY(), TOLERANCE)
        }
    }

    private fun DpRect.centreX(): Float = (left.value + right.value) / 2f

    private fun DpRect.centreY(): Float = (top.value + bottom.value) / 2f

    private companion object {
        const val CARD = "card"

        /** Half a dp: layout rounds to whole pixels, and one pixel is one dp here. */
        const val TOLERANCE = 0.5f

        val CLASS_NAMES = listOf("billing", "support", "sales", "refund", "general", "other", "complaint", "feedback")
    }
}
