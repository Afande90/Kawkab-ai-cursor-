package app.knotwork.design.components.pipelineeditor

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.knotwork.design.theme.KnotworkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * were squeezed to nothing. Its port labels then had a fixed 14 dp: at 200 % font scale a label
 * outgrew it and pushed the dots up onto the card's text.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w800dp-h600dp-mdpi")
class NodeCardPortLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /** One card to draw: its type and ports, an optional body line or error, and the font scale. */
    private data class Case(
        val type: NodeType,
        val ports: NodePorts = NodePorts.forType(type),
        val subtitle: String? = null,
        val error: NodeError? = null,
        val fontScale: Float = 1f,
    )

    private var shown by mutableStateOf(Case(NodeType.LITE_RT))

    /** The label line height the card was composed with — read back from the composition. */
    private var labelLineHeight = 0f

    @Test
    fun `given a single port when drawn then its dot is centred on the card's bottom edge`() {
        render()
        assertDotsWhereTheGeometrySays(onShortestCard = true)
    }

    @Test
    fun `given an input node without an inbound dot when drawn then its outbound dot is on the surface edge`() {
        shown = Case(NodeType.INPUT)
        render()
        assertDotsWhereTheGeometrySays(onShortestCard = true)
    }

    @Test
    fun `given the two branches of an IF when drawn then each dot is in its own slot`() {
        shown = Case(NodeType.IF_CONDITION)
        render()
        assertDotsWhereTheGeometrySays(onShortestCard = true)
    }

    @Test
    fun `given a router with three to eight classes when drawn then every dot is drawn in its slot`() {
        render()
        listOf(3, 4, 5, 6, 8).forEach { count ->
            shown = Case(NodeType.INTENT_ROUTER, router(count))
            composeTestRule.waitForIdle()
            assertDotsWhereTheGeometrySays(onShortestCard = true)
        }
    }

    @Test
    fun `given a body line or a runtime error when drawn then the dots stay on the surface's bottom edge`() {
        render()
        listOf(
            Case(NodeType.LITE_RT, subtitle = "gemma"),
            Case(NodeType.IF_CONDITION, subtitle = "x > 1"),
            Case(NodeType.IF_CONDITION, error = NodeError.Runtime("timed out")),
        ).forEach { case ->
            shown = case
            composeTestRule.waitForIdle()
            assertDotsWhereTheGeometrySays(onShortestCard = false)
        }
    }

    @Test
    fun `given a larger font scale when drawn then the dots stay on the surface's bottom edge`() {
        render()
        listOf(1.3f, 2f).forEach { scale ->
            listOf(Case(NodeType.LITE_RT), Case(NodeType.IF_CONDITION), Case(NodeType.INTENT_ROUTER, router(5)))
                .forEach { case ->
                    shown = case.copy(fontScale = scale)
                    composeTestRule.waitForIdle()
                    // Not 14 dp × scale: the density converts sp non-linearly, as Android 14 does on a device.
                    assertTrue("label line at $scale: $labelLineHeight", labelLineHeight > LABEL_LINE_AT_SCALE_1)
                    assertDotsWhereTheGeometrySays(onShortestCard = false)
                }
        }
    }

    private fun router(count: Int) = NodePorts.forType(NodeType.INTENT_ROUTER, intentClasses = CLASS_NAMES.take(count))

    private fun render() {
        composeTestRule.setContent {
            val case = shown
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, case.fontScale)) {
                labelLineHeight = nodePortLabelLineHeight().value
                KnotworkTheme(darkTheme = false) {
                    NodeCard(
                        type = case.type,
                        title = "Node",
                        subtitle = case.subtitle,
                        selected = false,
                        error = case.error,
                        multiSelected = false,
                        ports = case.ports,
                        modifier = Modifier.testTag(CARD),
                    )
                }
            }
        }
    }

    /**
     * Every dot of the shown card is in its slot, on the surface's edge, and where the editor
     * expects it: [NodePortGeometry.belowOutboundDots] above the card's bottom for the label line
     * the card was composed with — and, on the shortest card, on [NodePortGeometry.outboundDotY].
     */
    private fun assertDotsWhereTheGeometrySays(onShortestCard: Boolean) {
        val case = shown
        val ports = case.ports
        val count = ports.outbound.size
        val name = "${case.type} x$count, ${case.subtitle ?: case.error ?: "title only"}, scale ${case.fontScale}"
        val card = composeTestRule.onNodeWithTag(CARD).getBoundsInRoot()
        val surface = composeTestRule.onNodeWithTag(NODE_CARD_SURFACE_TEST_TAG).getBoundsInRoot()
        assertEquals("$name: card width", NodePortGeometry.cardWidth(count), card.width.value, TOLERANCE)

        val centreX = card.left.value + card.width.value / 2f
        if (ports.inbound > 0) {
            val inbound = composeTestRule.onNodeWithTag(NODE_CARD_INBOUND_DOT_TEST_TAG).getBoundsInRoot()
            assertEquals("$name: inbound dot x", centreX, inbound.centreX(), TOLERANCE)
            assertEquals(
                "$name: inbound dot y",
                card.top.value + NodePortGeometry.INBOUND_DOT_Y,
                inbound.centreY(),
                TOLERANCE,
            )
        }
        val dotLine = card.bottom.value - NodePortGeometry.belowOutboundDots(count, labelLineHeight)
        (0 until count).forEach { index ->
            val dot = composeTestRule.onNodeWithTag(NODE_CARD_OUTBOUND_DOT_TEST_TAG_PREFIX + index).getBoundsInRoot()
            assertEquals("$name, #$index: dot diameter", 2 * NodePortGeometry.DOT_RADIUS, dot.width.value, TOLERANCE)
            assertEquals(
                "$name, #$index: dot x",
                centreX + NodePortGeometry.outboundOffset(index, count),
                dot.centreX(),
                TOLERANCE,
            )
            assertEquals("$name, #$index: dot on the surface's edge", surface.bottom.value, dot.centreY(), TOLERANCE)
            assertEquals("$name, #$index: dot line from the card's bottom", dotLine, dot.centreY(), TOLERANCE)
            if (onShortestCard) {
                val expected = card.top.value + NodePortGeometry.outboundDotY(hasInbound = ports.inbound > 0)
                assertEquals("$name, #$index: dot line of the shortest card", expected, dot.centreY(), TOLERANCE)
            }
        }
    }

    private fun DpRect.centreX(): Float = (left.value + right.value) / 2f

    private fun DpRect.centreY(): Float = (top.value + bottom.value) / 2f

    private companion object {
        const val CARD = "card"

        /** Half a dp: layout rounds to whole pixels, and one pixel is one dp here. */
        const val TOLERANCE = 0.5f

        /** `LabelSm`'s line height at font scale 1. */
        const val LABEL_LINE_AT_SCALE_1 = NodePortGeometry.LABEL_LINE_HEIGHT

        val CLASS_NAMES = listOf("billing", "support", "sales", "refund", "general", "other", "complaint", "feedback")
    }
}
