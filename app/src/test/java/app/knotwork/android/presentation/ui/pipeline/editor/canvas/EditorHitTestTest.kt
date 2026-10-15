package app.knotwork.android.presentation.ui.pipeline.editor.canvas

import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.design.components.pipelineeditor.NodePortGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the editor's canvas-space hit-test geometry — the maths behind
 * connection creation. These cover the two regressions users hit:
 *
 *  - releasing a connection anywhere on the target node's card must connect (not only on a
 *    pixel-precise top-edge anchor), at any zoom — [hitTestInputNode];
 *  - a press on an outbound port must be recognised as a connection start so the canvas can
 *    decline to pan it — [hitTestOutboundPort].
 *
 * Canvas units are dp, so every coordinate below is a dp on the card (168 wide unless its
 * ports need more, 96 tall at most) and no display density or zoom is involved: the geometry
 * answers the same on every screen, which is the property the canvas-units change exists for.
 *
 * The pure geometry is exercised here (no Compose runtime); gesture arbitration itself is
 * device-only and out of scope for the JVM gate.
 */
class EditorHitTestTest {

    private fun node(id: String, type: NodeType, x: Float, y: Float): NodeModel =
        NodeModel(id = id, type = type, x = x, y = y)

    // A LITE_RT card at canvas origin spans (0,0)..(168,96).
    private val liteRt = node("n1", NodeType.LITE_RT, x = 0f, y = 0f)

    @Test
    fun `release on the node body connects to that node`() {
        // Card centre — the spot the old anchor-radius test missed at high zoom.
        val hit = hitTestInputNode(pointerCanvasX = 84f, pointerCanvasY = 48f, nodes = listOf(liteRt))
        assertEquals("n1", hit?.id)
    }

    @Test
    fun `release near the bottom of the node body still connects`() {
        val hit = hitTestInputNode(pointerCanvasX = 20f, pointerCanvasY = 90f, nodes = listOf(liteRt))
        assertEquals("n1", hit?.id)
    }

    @Test
    fun `release far from every node connects to nothing`() {
        val hit = hitTestInputNode(pointerCanvasX = 1000f, pointerCanvasY = 1000f, nodes = listOf(liteRt))
        assertNull(hit)
    }

    @Test
    fun `release between two nodes picks the nearer card`() {
        val far = node("n2", NodeType.LITE_RT, x = 500f, y = 500f)
        val hit = hitTestInputNode(pointerCanvasX = 84f, pointerCanvasY = 48f, nodes = listOf(liteRt, far))
        assertEquals("n1", hit?.id)
    }

    @Test
    fun `release near a target stacked just below the source connects to the target, not the source`() {
        // Source card at origin (centre y=48); target just below it (centre y=178). A release
        // in the gap near the target's top edge (y=100) is actually closer to the SOURCE centre
        // (52) than the target centre (78) — without excluding the source this resolves to a
        // self-drop and the connection is rejected. This is the tightly-stacked pair that
        // refused to connect while every other pair worked.
        val source = node("src", NodeType.LITE_RT, x = 0f, y = 0f)
        val target = node("tgt", NodeType.TOOL, x = 0f, y = 130f)
        val nodes = listOf(source, target)
        // Without excluding the source the buggy nearest-centre pick returns the source…
        assertEquals("src", hitTestInputNode(84f, 100f, nodes)?.id)
        // …excluding it (the real call passes the draft's source id) lands on the target.
        assertEquals("tgt", hitTestInputNode(84f, 100f, nodes, excludeNodeId = "src")?.id)
    }

    @Test
    fun `press on the single outbound port is recognised`() {
        val hit = hitTestOutboundPort(pointerCanvasX = 84f, pointerCanvasY = DOT_LINE, nodes = listOf(liteRt))
        assertNotNull(hit)
        assertEquals("n1", hit?.nodeId)
        assertEquals("", hit?.label)
    }

    @Test
    fun `press in empty canvas is not a port`() {
        val hit = hitTestOutboundPort(pointerCanvasX = 400f, pointerCanvasY = 400f, nodes = listOf(liteRt))
        assertNull(hit)
    }

    @Test
    fun `given a press when tested then a port owns exactly its target square around the dot`() {
        // The canvas must stand down wherever the node's port handler takes the press — the
        // HIT_TARGET square EditorNode centres on the dot — and pan everywhere else. The square
        // is drawn inside the card, so it is the same in canvas units at every zoom.
        val half = NodePortGeometry.HIT_TARGET / 2f
        assertNotNull("corner", hitTestOutboundPort(84f + half, DOT_LINE + half, listOf(liteRt)))
        assertNotNull("corner", hitTestOutboundPort(84f - half, DOT_LINE - half, listOf(liteRt)))
        assertNull("right of it", hitTestOutboundPort(84f + half + 1f, DOT_LINE, listOf(liteRt)))
        assertNull("below it", hitTestOutboundPort(84f, DOT_LINE + half + 1f, listOf(liteRt)))
    }

    @Test
    fun `IF node exposes two distinct outbound ports the press can target`() {
        val ifNode = node("if1", NodeType.IF_CONDITION, x = 0f, y = 0f)
        // Two ports share the 168 dp card's inner width evenly: ±36 dp around the centre (84), on the dot line.
        val left = hitTestOutboundPort(84f + NodePortGeometry.outboundOffset(0, 2), DOT_LINE, listOf(ifNode))
        val right = hitTestOutboundPort(84f + NodePortGeometry.outboundOffset(1, 2), DOT_LINE, listOf(ifNode))
        assertNotNull("Left True/False port must be grabbable", left)
        assertNotNull("Right True/False port must be grabbable", right)
        assertNotEquals("The two ports must carry different labels", left?.label, right?.label)
    }

    @Test
    fun `given a router with six routes when each dot is pressed then that route is the one grabbed`() {
        // The card grows to fit six 40 dp slots; each press on a dot — or anywhere in its
        // slot's target, up to the shared edge — grabs that route and no neighbour.
        val centre = router.x + NodePortGeometry.cardWidth(ROUTES.size) / 2f
        ROUTES.forEachIndexed { index, route ->
            val dotX = centre + NodePortGeometry.outboundOffset(index, ROUTES.size)
            val onDot = hitTestOutboundPort(dotX, DOT_LINE, listOf(router), routerEdges)
            val nearEdge = hitTestOutboundPort(dotX + 19f, DOT_LINE, listOf(router), routerEdges)
            assertEquals(route, onDot?.label)
            assertEquals(route, nearEdge?.label)
        }
    }

    @Test
    fun `given a router wider than the narrowest card when released on its right part then it connects`() {
        // Six routes make the card 264 dp wide; a drop past the 168 dp a card used to have is
        // still on it.
        val hit = hitTestInputNode(250f, 48f, nodes = listOf(router), connections = routerEdges)
        assertEquals("r1", hit?.id)
    }

    @Test
    fun `given a router wider than the narrowest card when its edges are anchored then they meet its dots`() {
        val ports = portsFor(router, outboundLabelsOf(router.id, routerEdges))
        val width = NodePortGeometry.cardWidth(ROUTES.size)
        assertEquals(264f, width)
        val inbound = inboundPortAnchor(router, ports)
        assertEquals(router.x + width / 2f, inbound.xCanvas)
        assertEquals(router.y + NodePortGeometry.INBOUND_DOT_Y, inbound.yCanvas)
        ROUTES.forEachIndexed { index, route ->
            val anchor = outboundPortAnchor(router, ports, route)
            assertEquals(route, router.x + width / 2f + NodePortGeometry.outboundOffset(index, 6), anchor.xCanvas)
            assertEquals(route, router.y + DOT_LINE, anchor.yCanvas)
        }
    }

    private val router = node("r1", NodeType.INTENT_ROUTER, x = 0f, y = 0f)

    // A router's routes come from its classes and from the labels of its edges; edges alone
    // are enough here.
    private val routerEdges = ROUTES.mapIndexed { index, route ->
        ConnectionModel(id = "e$index", sourceNodeId = "r1", targetNodeId = "t$index", label = route)
    }

    private companion object {
        val DOT_LINE = NodePortGeometry.outboundDotY(hasInbound = true)
        val ROUTES = listOf("billing", "support", "sales", "refund", "general", "other")
    }
}
