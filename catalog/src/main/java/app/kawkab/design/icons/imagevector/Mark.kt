package app.kawkab.design.icons.imagevector

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Kawkab brand mark — a planet and its ring, on a 24×24 grid. The same motif
 * is the launcher foreground, so About, splash, onboarding and the launcher
 * show one glyph.
 */
internal val knotworkMarkIcon: ImageVector by lazy { build() }

private const val RING_STROKE = 1.5f

private fun build(): ImageVector = iconBuilder("Mark")
    .fillPath(circlePath(cx = 12f, cy = 12f, r = 4.5f))
    .strokePath(
        "M 3 13.5 A 9 3.2 -28 1 1 21 10.5 A 9 3.2 -28 1 1 3 13.5",
        strokeWidth = RING_STROKE,
    )
    .build()
