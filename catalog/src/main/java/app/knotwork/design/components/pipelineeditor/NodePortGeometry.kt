package app.knotwork.design.components.pipelineeditor

import kotlin.math.max

/**
 * Where a [NodeCard]'s ports sit, in dp — the one source the card draws from and the editor
 * canvas places its edges and touch targets by.
 *
 * The card and the canvas used to lay ports out separately: the card in a row as wide as its
 * labels, the canvas by a spacing formula. With short labels they agreed; an Intent Router with
 * four classes drifted, and with five or six two dots were not drawn at all while their touch
 * targets were. Both now read this object.
 *
 * **Width grows with the outbound ports.** Each outbound port has a slot at least
 * [MIN_SLOT_WIDTH] wide — room for its dot, its label and a finger — so a card with more ports
 * than fit in [MIN_CARD_WIDTH] grows: `max(168, 24 + 40 × ports)` dp. Up to three ports keep the
 * 168 dp card; six are 264 dp. The slots share the card's inner width evenly.
 *
 * Vertical positions are measured from the top of the card's layout box (`NodeModel.y` on the
 * canvas), which includes the inbound dot's half above the card when there is one.
 */
object NodePortGeometry {

    /** Width of a card whose ports all fit — every card with up to three outbound ports. */
    const val MIN_CARD_WIDTH: Float = 168f

    /** Horizontal padding between the card edge and the outermost port slot, each side. */
    const val SIDE_PADDING: Float = 12f

    /** Narrowest an outbound port's slot gets: its dot, its label, and room for a finger. */
    const val MIN_SLOT_WIDTH: Float = 40f

    /** Radius of a port dot — also how far a dot protrudes past the card edge. */
    const val DOT_RADIUS: Float = 6f

    /** Gap between an outbound dot and its label. */
    const val LABEL_GAP: Float = 4f

    /** Height of the label line under an outbound dot. */
    const val LABEL_LINE_HEIGHT: Float = 14f

    /** Height of the card's surface when it shows a title only — the shortest it renders. */
    const val MIN_SURFACE_HEIGHT: Float = 64f

    /**
     * Side of the square in which the canvas accepts a press as a press on a port, centred on
     * the dot. As wide as the narrowest slot, so neighbouring ports never share a press.
     */
    const val HIT_TARGET: Float = MIN_SLOT_WIDTH

    /**
     * Width of a card with [outboundCount] outbound ports.
     *
     * @param outboundCount Number of outbound ports.
     * @return `max(168, 24 + 40 × outboundCount)` dp.
     */
    fun cardWidth(outboundCount: Int): Float = max(MIN_CARD_WIDTH, 2 * SIDE_PADDING + outboundCount * MIN_SLOT_WIDTH)

    /**
     * Width of each outbound port's slot: the card's inner width shared evenly, never under
     * [MIN_SLOT_WIDTH]. A single port takes the whole inner width.
     *
     * @param outboundCount Number of outbound ports.
     * @return The slot width in dp.
     */
    fun slotWidth(outboundCount: Int): Float = (cardWidth(outboundCount) - 2 * SIDE_PADDING) / max(1, outboundCount)

    /**
     * Horizontal offset of outbound port [index]'s dot from the card's centre line.
     *
     * @param index The port's position, left to right, from 0.
     * @param count Number of outbound ports.
     * @return The offset in dp; negative is left of centre, `0` for a single port.
     */
    fun outboundOffset(index: Int, count: Int): Float =
        if (count <= 1) 0f else (index - (count - 1) / 2f) * slotWidth(count)

    /**
     * Distance from the top of the card's layout box to the inbound dot's centre — the surface's
     * top edge, half the dot above it.
     */
    const val INBOUND_DOT_Y: Float = DOT_RADIUS

    /**
     * Distance from the top of the card's layout box to the outbound dots' centres on a card at
     * its shortest — the surface's bottom edge. A card with a body line or a runtime error is
     * taller, and its dots sit lower by as much.
     *
     * @param hasInbound Whether the card has an inbound port, whose dot adds [DOT_RADIUS] above
     *   the surface.
     * @return The distance in dp.
     */
    fun outboundDotY(hasInbound: Boolean): Float = (if (hasInbound) DOT_RADIUS else 0f) + MIN_SURFACE_HEIGHT

    /**
     * Distance from the outbound dots' centres down to the bottom of the card's layout box: the
     * dot's lower half, and — when the ports are labelled — the gap and the label line.
     *
     * @param outboundCount Number of outbound ports; more than one are labelled.
     * @return The distance in dp; `0` when there are no outbound ports.
     */
    fun belowOutboundDots(outboundCount: Int): Float = when {
        outboundCount == 0 -> 0f
        outboundCount == 1 -> DOT_RADIUS
        else -> DOT_RADIUS + LABEL_GAP + LABEL_LINE_HEIGHT
    }
}
