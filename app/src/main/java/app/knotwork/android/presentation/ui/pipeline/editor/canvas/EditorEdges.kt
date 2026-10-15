package app.knotwork.android.presentation.ui.pipeline.editor.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.knotwork.android.domain.models.ConnectionModel
import app.knotwork.android.domain.models.NodeModel
import app.knotwork.android.domain.models.NodeType
import app.knotwork.android.domain.models.RouteLabels
import app.knotwork.android.presentation.ui.pipeline.editor.config.NodeConfigCodec
import app.knotwork.android.presentation.ui.pipeline.editor.config.NodeTypeMapper
import app.knotwork.android.presentation.ui.pipeline.editor.core.BezierEdge
import app.knotwork.android.presentation.ui.pipeline.editor.core.CanvasTransform
import app.knotwork.android.presentation.ui.pipeline.editor.core.NodeCardFootprint
import app.knotwork.design.components.pipelineeditor.EvaluationConfig
import app.knotwork.design.components.pipelineeditor.IntentRouterConfig
import app.knotwork.design.components.pipelineeditor.NodePortGeometry
import app.knotwork.design.components.pipelineeditor.NodePorts
import app.knotwork.design.components.pipelineeditor.OutboundPort
import app.knotwork.design.theme.KnotworkTheme

/**
 * Canvas-space coordinates of one port anchor (inbound or one of N outbound ports).
 *
 * Anchors are computed from the node's top-left corner plus the canonical card geometry
 * in [NodeCardFootprint]. Canvas units are dp, the unit the card is laid out in, so the
 * geometry needs no density conversion.
 */
internal data class PortAnchor(val xCanvas: Float, val yCanvas: Float)

/**
 * Width of [node]'s card on the canvas: one slot per outbound port ([NodePortGeometry.cardWidth]).
 * On an Intent Router that is one per class and one per edge label no class names, so the edges
 * leaving the node count — the same ports [portsFor] gives the card.
 *
 * @param node the node.
 * @param connections the graph's edges.
 * @return the card width in canvas units (dp).
 */
internal fun cardWidthOf(node: NodeModel, connections: List<ConnectionModel>): Float =
    NodePortGeometry.cardWidth(portsFor(node, outboundLabelsOf(node.id, connections)).outbound.size)

/**
 * Inbound port anchor — the centre of the single dot on the top edge.
 *
 * @param node the node.
 * @param ports the node's ports, which set its card's width.
 * @return the anchor in canvas space.
 */
internal fun inboundPortAnchor(node: NodeModel, ports: NodePorts): PortAnchor = PortAnchor(
    xCanvas = node.x + NodePortGeometry.cardWidth(ports.outbound.size) / 2f,
    yCanvas = node.y + NodePortGeometry.INBOUND_DOT_Y,
)

/**
 * Outbound port anchor for the port with [portLabel] on [node] (using [ports] to enumerate).
 * For nodes with a single unlabelled outbound port the label is empty; for IF / Queue / Eval
 * / IntentRouter the label distinguishes which port the edge originates from.
 *
 * A labelled edge always has its own port: [portsFor], given the node's outgoing labels,
 * adds one for a router label no class names and surfaces the Retry port a Retry edge
 * needs, and the importer spells fixed branch labels as their ports do. What still falls
 * back to index 0 is an unlabelled edge on a node with only labelled ports — the engine's
 * lone default way out of an IF_CONDITION.
 */
internal fun outboundPortAnchor(node: NodeModel, ports: NodePorts, portLabel: String?): PortAnchor {
    val outbound = ports.outbound
    val centreX = node.x + NodePortGeometry.cardWidth(outbound.size) / 2f
    val outY = node.y + NodePortGeometry.outboundDotY(hasInbound = ports.inbound > 0)
    if (outbound.isEmpty()) return PortAnchor(centreX, outY)
    val matched = outbound.indexOfFirst { matchesPort(it, portLabel) }
    val index = if (matched >= 0) matched else 0
    return PortAnchor(xCanvas = centreX + NodePortGeometry.outboundOffset(index, outbound.size), yCanvas = outY)
}

/**
 * Canvas-space axis-aligned bounds of [node]'s card: top-left `(node.x, node.y)` to
 * bottom-right `(node.x + width, node.y + height)`. Uses the **max** card height so the
 * rectangle covers a node that renders a runtime-error line. Used by the canvas to decide
 * which node a connection drag was released onto (whole-card target, not a tiny anchor).
 */
internal data class NodeBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

internal fun nodeCanvasBounds(node: NodeModel, width: Float): NodeBounds = NodeBounds(
    left = node.x,
    top = node.y,
    right = node.x + width,
    bottom = node.y + NodeCardFootprint.MAX_HEIGHT,
)

/**
 * Canvas-space anchor of every outbound port on [node], paired with its label (`""` for the
 * single default port). Mirrors [outboundPortAnchor]'s geometry but enumerates all ports, so
 * the canvas gesture handler can decide whether a press landed on a port (→ start a
 * connection) instead of panning.
 */
internal fun outboundPortAnchors(
    node: NodeModel,
    outboundLabels: List<String?> = emptyList(),
): List<Pair<String, PortAnchor>> {
    val ports = portsFor(node, outboundLabels)
    val outbound = ports.outbound
    if (outbound.isEmpty()) return emptyList()
    val centreX = node.x + NodePortGeometry.cardWidth(outbound.size) / 2f
    val outY = node.y + NodePortGeometry.outboundDotY(hasInbound = ports.inbound > 0)
    return outbound.mapIndexed { index, port ->
        port.label to PortAnchor(
            xCanvas = centreX + NodePortGeometry.outboundOffset(index, outbound.size),
            yCanvas = outY,
        )
    }
}

/**
 * Matches a connection's [ConnectionModel.label] against an [OutboundPort] by the rule the
 * engine routes by ([RouteLabels.matches], case-insensitive), or the connection's `null` /
 * blank label against a `Default` port.
 */
private fun matchesPort(port: OutboundPort, label: String?): Boolean = when {
    label.isNullOrBlank() -> port is OutboundPort.Default
    else -> RouteLabels.matches(label, port.label)
}

/**
 * The labels of the edges leaving [nodeId], in [connections] order — what [portsFor]
 * needs to draw every branch the run can take.
 *
 * @param nodeId the source node.
 * @param connections the graph's edges.
 * @return one entry per outgoing edge, `null` for an unlabelled one.
 */
internal fun outboundLabelsOf(nodeId: String, connections: List<ConnectionModel>): List<String?> =
    connections.filter { it.sourceNodeId == nodeId }.map { it.label }

/**
 * Builds the catalog `NodePorts` for a domain node, threading through the per-type
 * overrides that depend on the decoded `NodeConfig`:
 *
 *  - `INTENT_ROUTER` → one [OutboundPort.Custom] per declared class, then one per
 *    distinct outgoing edge label no class names. The run chooses among the edge labels,
 *    not the classes, so a branch the sheet does not list still gets a port of its own
 *    instead of borrowing the first one.
 *  - `EVALUATION` → the `Retry` port is surfaced when `maxRetries > 0`, or when an edge is
 *    labelled Retry: the run takes it on a Retry verdict whatever the retry count says.
 *
 *  All other types ignore the extra parameters. Decoding is cheap (small JSON
 *  documents, fast fallback to legacy flat fields), and EditorCanvas memoises the
 *  result per node so port lookup during a hot drag doesn't re-decode.
 */
internal fun portsFor(node: NodeModel, outboundLabels: List<String?> = emptyList()): NodePorts {
    val catalogType = NodeTypeMapper.toCatalog(node.type)
    val decoded = NodeConfigCodec.decode(node)
    val declared = (decoded as? IntentRouterConfig)?.classes?.map { it.name }.orEmpty()
    val intentClasses = if (node.type == NodeType.INTENT_ROUTER) {
        outboundLabels.filterNotNull().filter { it.isNotBlank() }.fold(declared) { names, label ->
            if (names.any { RouteLabels.matches(label, it) }) names else names + label
        }
    } else {
        declared
    }
    val maxRetries = (decoded as? EvaluationConfig)?.maxRetries ?: 0
    val retryWired = outboundLabels.any { RouteLabels.matches(it, RouteLabels.RETRY) }
    return NodePorts.forType(
        type = catalogType,
        intentClasses = intentClasses,
        maxRetries = if (retryWired) maxOf(maxRetries, 1) else maxRetries,
    )
}

/**
 * Draws every edge in [connections] as a cubic Bezier between the source's outbound
 * anchor and the target's inbound anchor. Highlights the edge identified by
 * [highlightedConnectionId] (the run-trace cursor / hover) and applies a traveling
 * accent dot whose duration is derived from the edge's arc length so motion stays
 * visually constant at the spec's 40 dp/s.
 *
 * @param connections graph edges, in stable iteration order (typically the persisted list).
 * @param nodesById map of `id → NodeModel` used to look up anchor positions.
 * @param transform pan / zoom applied uniformly to all paths.
 * @param connectionDraft optional in-flight connection (drag from a port to the pointer).
 * If present, an extra preview edge is drawn between the source port and the live pointer
 * in dashed style.
 */
@Composable
internal fun EditorEdges(
    connections: List<ConnectionModel>,
    nodesById: Map<String, NodeModel>,
    transform: CanvasTransform,
    connectionDraft: ConnectionDraftDrawData?,
    selectedEdgeId: String?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val edgeColor = KnotworkTheme.extended.divider
    val accentColor = KnotworkTheme.extended.outlineStrong
    val strokeWidth = with(density) { 2.dp.toPx() }
    // A node's ports come from its decoded config and its edges. Work them out once per graph
    // change rather than for both ends of every edge on every frame of the run-trace animation.
    val portsById = remember(nodesById, connections) {
        nodesById.mapValues { (id, node) -> portsFor(node, outboundLabelsOf(id, connections)) }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        connections.forEach { c ->
            val source = nodesById[c.sourceNodeId] ?: return@forEach
            val target = nodesById[c.targetNodeId] ?: return@forEach
            // Per-port anchors: edges originate at the dot matching the connection label
            // (e.g. Item / Done on QUEUE, True / False on IF) rather than the node centre.
            val srcAnchor = outboundPortAnchor(source, portsById.getValue(source.id), c.label)
            val tgtAnchor = inboundPortAnchor(target, portsById.getValue(target.id))
            val sx = transform.canvasToScreenX(srcAnchor.xCanvas)
            val sy = transform.canvasToScreenY(srcAnchor.yCanvas)
            val tx = transform.canvasToScreenX(tgtAnchor.xCanvas)
            val ty = transform.canvasToScreenY(tgtAnchor.yCanvas)
            val (c0, c1) = BezierEdge.controlPoints(sx, sy, tx, ty)
            val isSelected = c.id == selectedEdgeId
            val path = Path().apply {
                moveTo(sx, sy)
                cubicTo(c0.first, c0.second, c1.first, c1.second, tx, ty)
            }
            val strokeMultiplier = if (isSelected) EDGE_STROKE_SELECTED_FACTOR else 1f
            val strokeColor = if (isSelected) accentColor else edgeColor
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = strokeWidth * strokeMultiplier),
            )
        }
        if (connectionDraft != null) {
            drawPreviewEdge(
                src = connectionDraft.sourceScreen,
                pointer = connectionDraft.pointerScreen,
                accentColor = accentColor,
                strokeWidth = strokeWidth,
            )
        }
    }
}

/**
 * Snapshot of an in-flight connection draft in screen space — pre-projected so the
 * draw layer doesn't need access to [CanvasTransform].
 *
 * @property sourceScreen screen-space outbound anchor of the source node.
 * @property pointerScreen live pointer position.
 */
internal data class ConnectionDraftDrawData(val sourceScreen: Offset, val pointerScreen: Offset)

/**
 * Returns the id of the connection nearest to a canvas-space tap, or `null` when no
 * edge is within the screen-space [toleranceDp] of the point.
 *
 * Used by the canvas tap handler to implement "tap an edge → select it". The tolerance is
 * a screen dp, and a canvas unit is a dp at zoom 1, so dividing by the transform's scale
 * converts it to canvas units — the user gets the same visual hit area at any zoom.
 */
internal fun hitTestEdge(
    pointerCanvasX: Float,
    pointerCanvasY: Float,
    connections: List<ConnectionModel>,
    nodesById: Map<String, NodeModel>,
    transform: CanvasTransform,
    toleranceDp: Float = EDGE_HIT_TOLERANCE_DP,
): String? {
    val toleranceCanvas = toleranceDp / transform.scale
    var bestId: String? = null
    var bestDist = Float.MAX_VALUE
    connections.forEach { c ->
        val src = nodesById[c.sourceNodeId] ?: return@forEach
        val tgt = nodesById[c.targetNodeId] ?: return@forEach
        val srcAnchor = outboundPortAnchor(src, portsFor(src, outboundLabelsOf(src.id, connections)), c.label)
        val tgtAnchor = inboundPortAnchor(tgt, portsFor(tgt, outboundLabelsOf(tgt.id, connections)))
        val (cp0, cp1) = BezierEdge.controlPoints(
            srcAnchor.xCanvas,
            srcAnchor.yCanvas,
            tgtAnchor.xCanvas,
            tgtAnchor.yCanvas,
        )
        val d = BezierEdge.distanceToPoint(
            px = pointerCanvasX,
            py = pointerCanvasY,
            x0 = srcAnchor.xCanvas,
            y0 = srcAnchor.yCanvas,
            c0x = cp0.first,
            c0y = cp0.second,
            c1x = cp1.first,
            c1y = cp1.second,
            x1 = tgtAnchor.xCanvas,
            y1 = tgtAnchor.yCanvas,
        )
        if (d < toleranceCanvas && d < bestDist) {
            bestId = c.id
            bestDist = d
        }
    }
    return bestId
}

/**
 * Tap-on-edge tolerance in screen-space dp. Generous because edges are thin (2 dp stroke)
 * and pure paint output — the user must be able to land within a finger's-width to select
 * an edge for the toolbar Delete action.
 */
private const val EDGE_HIT_TOLERANCE_DP = 24f

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPreviewEdge(
    src: Offset,
    pointer: Offset,
    accentColor: Color,
    strokeWidth: Float,
) {
    // Draw a straight line for the in-flight connection rather than the cubic Bezier the
    // committed edges use. `BezierEdge.controlPoints` always pushes the source handle
    // DOWNWARD (matching the outbound port at the card's bottom) and the target handle
    // UPWARD; that produces a loop-de-loop when the user drags upward / sideways past the
    // source port, with the preview rendering far from the finger. A straight line tracks
    // the finger predictably and the curve appears once the connection lands on a target.
    drawLine(
        color = accentColor,
        start = src,
        end = pointer,
        strokeWidth = strokeWidth * EDGE_PREVIEW_STROKE_FACTOR,
    )
}

private const val EDGE_STROKE_SELECTED_FACTOR = 2f
private const val EDGE_PREVIEW_STROKE_FACTOR = 1.5f
