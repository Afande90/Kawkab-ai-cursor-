package app.knotwork.design.components.pipelineeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NodePortGeometry]: the card grows so every outbound port keeps a slot of at least 40 dp, the
 * slots share the inner width evenly and stay inside the card, and the dot lines sit where the card
 * draws them.
 */
class NodePortGeometryTest {

    @Test
    fun `given up to three ports when sized then the card stays at its narrowest`() {
        listOf(0, 1, 2, 3).forEach { count ->
            assertEquals("$count ports", NodePortGeometry.MIN_CARD_WIDTH, NodePortGeometry.cardWidth(count))
        }
    }

    @Test
    fun `given more ports than fit when sized then the card grows by a slot per port`() {
        assertEquals(184f, NodePortGeometry.cardWidth(4))
        assertEquals(224f, NodePortGeometry.cardWidth(5))
        assertEquals(264f, NodePortGeometry.cardWidth(6))
        assertEquals(344f, NodePortGeometry.cardWidth(8))
    }

    @Test
    fun `given any port count when laid out then every slot is at least 40 dp and inside the card`() {
        (1..10).forEach { count ->
            val width = NodePortGeometry.cardWidth(count)
            val slot = NodePortGeometry.slotWidth(count)
            assertTrue("$count ports: slot $slot", slot >= NodePortGeometry.MIN_SLOT_WIDTH)
            (0 until count).forEach { index ->
                val centre = width / 2f + NodePortGeometry.outboundOffset(index, count)
                assertTrue(
                    "$count ports, #$index left edge",
                    centre - slot / 2f >= NodePortGeometry.SIDE_PADDING - 0.01f,
                )
                assertTrue(
                    "$count ports, #$index right edge",
                    centre + slot / 2f <= width - NodePortGeometry.SIDE_PADDING + 0.01f,
                )
            }
        }
    }

    @Test
    fun `given several ports when laid out then neighbours are a slot apart and symmetric about the centre`() {
        (2..8).forEach { count ->
            val offsets = (0 until count).map { NodePortGeometry.outboundOffset(it, count) }
            offsets.zipWithNext().forEach { (a, b) ->
                assertEquals("$count ports", NodePortGeometry.slotWidth(count), b - a, 0.001f)
            }
            assertEquals("$count ports", 0f, offsets.first() + offsets.last(), 0.001f)
        }
        assertEquals(0f, NodePortGeometry.outboundOffset(0, 1))
    }

    @Test
    fun `given a card when its dot lines are read then they match the card's insets`() {
        assertEquals(6f, NodePortGeometry.INBOUND_DOT_Y)
        assertEquals(70f, NodePortGeometry.outboundDotY(hasInbound = true))
        assertEquals(64f, NodePortGeometry.outboundDotY(hasInbound = false))
        assertEquals(0f, NodePortGeometry.belowOutboundDots(0))
        assertEquals(6f, NodePortGeometry.belowOutboundDots(1))
        assertEquals(24f, NodePortGeometry.belowOutboundDots(4))
    }
}
