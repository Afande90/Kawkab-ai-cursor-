package app.knotwork.android.presentation.ui.pipeline.editor.core

import app.knotwork.design.components.pipelineeditor.NodePortGeometry

/**
 * Footprint of a node card on the editor canvas, in canvas units — which are dp.
 *
 * The design system sizes `NodeCard` in dp (at least 168 dp wide, 64–96 dp tall), and
 * the canvas stores node positions in the same unit, so these numbers can be used
 * directly against `NodeModel.x / y` with no density conversion anywhere.
 *
 * A card's **width** grows with its outbound ports and its port positions are the
 * design system's [NodePortGeometry] — the one source the card draws from. The
 * canvas reads a node's width through `cardWidthOf`, which counts its ports, so port
 * anchors, hit tests, fit-to-view, the mini-map, the auto-layout and the guard that
 * keeps bundled presets from overlapping cannot drift from the card.
 */
object NodeCardFootprint {

    /** Width of a card with up to three outbound ports — the narrowest a card renders. */
    const val MIN_WIDTH: Float = NodePortGeometry.MIN_CARD_WIDTH

    /**
     * Tallest a card renders (`NodeCardMaxHeight`), reached when it shows a body or
     * error line. Used wherever the whole card must be covered: release targets,
     * fit-to-view bounds and overlap checks.
     */
    const val MAX_HEIGHT: Float = 96f
}
