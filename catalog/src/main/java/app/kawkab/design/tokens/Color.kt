@file:Suppress("MagicNumber") // Token file: every literal IS the data; named constants would just rename the same hex.

package app.kawkab.design.tokens

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Knotwork color tokens — port of the canonical OKLCH tokens into Compose-ready
 * sRGB hex values.
 *
 * Conversion pipeline: OKLCH → linear sRGB → sRGB hex, chroma clipped to gamut.
 * The ramp below is the imported OKLCH export. The three Kawkab pins that
 * follow are owner colours and are set by hex on purpose.
 *
 * Kawkab (29 Oct 2026) pins three owner colours on top of the ramp:
 * deep-space ground [DeepSpace], electric indigo [Indigo] for the primary
 * role only, and [NeedsYou] amber for confirmation. The node hues below stay
 * put so a pipeline reads the same colour in either theme. Signal hues
 * (155 success / 80 warn / 25 error) stay fixed and are not the needs-you amber.
 */
object KnotworkPalette {
    /** Deep-space ground. Dark background and the launcher plate. */
    val DeepSpace = Color(0xFF070A14)

    /** Electric indigo. Primary role only — not a surface fill. */
    val Indigo = Color(0xFF5457E8)

    /** Amber for "needs you": the confirmation strip and the sensitive risk. */
    val NeedsYou = Color(0xFFF2B45A)

    // Accent ramp (hue 70, warm amber). Not the primary role.
    val Accent50 = Color(0xFFFFEFDD)
    val Accent100 = Color(0xFFFBE0C1)
    val Accent200 = Color(0xFFF0C595)
    val Accent300 = Color(0xFFE1AC6E)
    val Accent400 = Color(0xFFD49648)
    val Accent500 = Color(0xFFC48225) // MANUAL source rail (light); not the primary role
    val Accent600 = Color(0xFFA76C12)
    val Accent700 = Color(0xFF81520A)
    val Accent800 = Color(0xFF54360B)
    val Accent900 = Color(0xFF331F05)

    // Signal (hue-locked, never re-themed by accent)
    val SignalSuccess = Color(0xFF43A96E)
    val SignalWarn = Color(0xFFD5A13C)
    val SignalError = Color(0xFFD55753)

    // Per-NodeType hues (13) — identical in both themes so pipelines read
    // identically in light and dark.
    val NodeInput = Color(0xFFC48225)
    val NodeIntentRouter = Color(0xFF9283DC)
    val NodeIfCondition = Color(0xFF43A96E)
    val NodeClarification = Color(0xFFD49648)

    val NodeLiteRt = Color(0xFFA76C12)
    val NodeCloud = Color(0xFF5F9DB0)
    val NodeTool = Color(0xFFD5A13C)
    val NodeDecomposition = Color(0xFF00A8A2)
    val NodeQueueProcessor = Color(0xFFC670AB)
    val NodeEvaluation = Color(0xFFD5725A)
    val NodeSummary = Color(0xFF359BD9)
    val NodeOutput = Color(0xFF81520A)

    // Deep indigo for `NodeType.PIPELINE` — occupies the empty slot between
    // IntentRouter violet (~290°) and Summary/Cloud blue (~220°). Darker and
    // more saturated than its neighbours (L 0.47, oklch(0.47 0.16 274)) so it
    // reads as a separate family at a glance; the lower lightness also keeps
    // `headerOnColor` firmly in the white-foreground band (L < 0.5, 7.16:1).
    val NodePipeline = Color(0xFF424DB2)
}

/**
 * Light-theme surface and role tokens consumed by both the Material3
 * [ColorScheme] and the Knotwork-specific [KnotworkExtendedColors].
 */
object KnotworkLight {
    val Surface0 = Color(0xFFF6F7FB)
    val Surface1 = Color(0xFFEEF0F7)
    val Surface2 = Color(0xFFE6E8F2)
    val Surface3 = Color(0xFFDCDFEA)
    val Surface4 = Color(0xFFD2D6E4)
    val SurfaceInv = Color(0xFF12141C)

    val OnSurface = Color(0xFF12141C)
    val OnSurface2 = Color(0xFF3A3E4C)
    val OnSurfaceMuted = Color(0xFF6A6E7C)
    val OnSurfaceDim = Color(0xFF9094A2)

    val Outline = Color(0xFFD0D3E0)
    val OutlineStrong = Color(0xFFA8ADBC)
    val Divider = Color(0xFFE2E4EE)

    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFE4E6FC)
    val OnPrimaryContainer = Color(0xFF1E2070)

    // Tertiary container — lighter cool-blue tint of `NodeCloud` (#5F9DB0)
    // paired with a near-black on-tone for AA contrast on the container.
    val TertiaryContainer = Color(0xFFD6E5EB)
    val OnTertiaryContainer = Color(0xFF173039)

    val RiskReadonly = Color(0xFF5F9DB0)
    val RiskSensitive = KnotworkPalette.NeedsYou
    val RiskDestructive = KnotworkPalette.SignalError

    // Chat bubble pairs: user / agent / tool, each bg + fg.
    val ChatUserBg = PrimaryContainer
    val ChatUserFg = OnPrimaryContainer
    val ChatAgentBg = Surface1
    val ChatAgentFg = OnSurface
    val ChatToolBg = Surface2
    val ChatToolFg = OnSurface2

    val ConsoleBg = Color(0xFF191511)
    val ConsoleFg = Color(0xFFE7E4E0)
    val ConsoleTag = KnotworkPalette.Accent300

    // Memory source-tag provenance: AUTO hue 220 (blue),
    // MANUAL hue 70 (brand amber), COMPACT hue 285 (violet). bg / fg / 3px rail.
    val MemAutoBg = Color(0xFFD6F0F9)
    val MemAutoFg = Color(0xFF005E78)
    val MemAutoRail = Color(0xFF1F9BBA)
    val MemManualBg = KnotworkPalette.Accent100
    val MemManualFg = KnotworkPalette.Accent800
    val MemManualRail = KnotworkPalette.Accent500
    val MemCompactBg = Color(0xFFE8E9FF)
    val MemCompactFg = Color(0xFF534F8D)
    val MemCompactRail = Color(0xFF807CC6)
}

/**
 * Dark-theme surface and role tokens consumed by both the Material3
 * [ColorScheme] and the Knotwork-specific [KnotworkExtendedColors].
 */
object KnotworkDark {
    val Surface0 = KnotworkPalette.DeepSpace
    val Surface1 = Color(0xFF10141F)
    val Surface2 = Color(0xFF171C2C)
    val Surface3 = Color(0xFF1F2638)
    val Surface4 = Color(0xFF2A3148)
    val SurfaceInv = Color(0xFFF4F6FB)

    val OnSurface = Color(0xFFEEF1F8)
    val OnSurface2 = Color(0xFFC5CAD6)
    val OnSurfaceMuted = Color(0xFF8E95A8)
    val OnSurfaceDim = Color(0xFF5E6578)

    val Outline = Color(0xFF343B52)
    val OutlineStrong = Color(0xFF4E5670)
    val Divider = Color(0xFF222838)

    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFF2A2D6B)
    val OnPrimaryContainer = Color(0xFFD6D8FF)

    // Tertiary container — dark cool-blue tone paired with a light cyan
    // foreground; symmetric to the light-theme TertiaryContainer.
    val TertiaryContainer = Color(0xFF1A3B47)
    val OnTertiaryContainer = Color(0xFFC4DDE5)

    val RiskReadonly = Color(0xFF77B6CA)
    val RiskSensitive = KnotworkPalette.NeedsYou
    val RiskDestructive = Color(0xFFF97770)

    // Chat bubble pairs: user / agent / tool, each bg + fg.
    val ChatUserBg = Color(0xFF232656)
    val ChatUserFg = Color(0xFFD6D8FF)
    val ChatAgentBg = Surface2
    val ChatAgentFg = OnSurface
    val ChatToolBg = Surface3
    val ChatToolFg = OnSurface2

    val ConsoleBg = KnotworkPalette.DeepSpace
    val ConsoleFg = Color(0xFFDAD7D3)
    val ConsoleTag = KnotworkPalette.Accent300

    // Memory source-tag provenance — dark variants.
    val MemAutoBg = Color(0xFF07333F)
    val MemAutoFg = Color(0xFF7ED2ED)
    val MemAutoRail = Color(0xFF4AADC9)
    val MemManualBg = Color(0xFF462D0B)
    val MemManualFg = KnotworkPalette.Accent100
    val MemManualRail = KnotworkPalette.Accent300
    val MemCompactBg = Color(0xFF2B2A46)
    val MemCompactFg = Color(0xFFBDBCFD)
    val MemCompactRail = Color(0xFF928FD4)
}

/**
 * Builds the Material3 [ColorScheme] for the light Knotwork palette.
 *
 * Knotwork roles are mapped onto Material3 slots as follows:
 *  - `primary` → [KnotworkPalette.Indigo] in both themes. White on indigo
 *    clears the UI-contrast floor. Indigo is not used as a surface fill.
 *  - `secondary` → a deeper indigo, still not a surface fill.
 *  - `tertiary` → readonly-risk hue (the cool blue-cyan node colour)
 *  - `error` → [KnotworkPalette.SignalError] / dark equivalent
 *  - surfaces map to the Knotwork surface ramp
 *
 * For Knotwork-specific roles that have no Material3 slot (chat bubbles,
 * console surfaces, the three risk levels and 12 node hues), reach for
 * [KnotworkExtendedColors] via `KnotworkTheme.extended`.
 *
 * @return a freshly built [ColorScheme]; cheap enough to allocate on every
 * theme switch, so callers do not need to memoise.
 */
fun knotworkLightColorScheme(): ColorScheme = lightColorScheme(
    primary = KnotworkPalette.Indigo,
    onPrimary = KnotworkLight.OnPrimary,
    primaryContainer = KnotworkLight.PrimaryContainer,
    onPrimaryContainer = KnotworkLight.OnPrimaryContainer,
    secondary = Color(0xFF3A3CC4),
    onSecondary = KnotworkLight.OnPrimary,
    secondaryContainer = KnotworkLight.Surface3,
    onSecondaryContainer = KnotworkLight.OnSurface,
    tertiary = KnotworkPalette.NodeCloud,
    onTertiary = KnotworkLight.OnPrimary,
    tertiaryContainer = KnotworkLight.TertiaryContainer,
    onTertiaryContainer = KnotworkLight.OnTertiaryContainer,
    error = KnotworkPalette.SignalError,
    onError = KnotworkLight.OnPrimary,
    errorContainer = Color(0xFFFADCDA),
    onErrorContainer = Color(0xFF5A1410),
    background = KnotworkLight.Surface0,
    onBackground = KnotworkLight.OnSurface,
    surface = KnotworkLight.Surface0,
    onSurface = KnotworkLight.OnSurface,
    surfaceVariant = KnotworkLight.Surface2,
    onSurfaceVariant = KnotworkLight.OnSurface2,
    surfaceTint = KnotworkPalette.Indigo,
    inverseSurface = KnotworkLight.SurfaceInv,
    inverseOnSurface = KnotworkLight.OnPrimary,
    inversePrimary = KnotworkPalette.Indigo,
    outline = KnotworkLight.Outline,
    outlineVariant = KnotworkLight.Divider,
    scrim = Color(0x66000000),
    // Material3 expanded surface ramp (M3 1.1+): bind every container step
    // to the Knotwork Surface0..4 ramp so tonal containers in libraries
    // that read these slots (NavigationBar, BottomSheet, FilledCard, …)
    // stay on-brand instead of inheriting Material baseline tints.
    surfaceBright = KnotworkLight.Surface0,
    surfaceDim = KnotworkLight.Surface3,
    surfaceContainerLowest = KnotworkLight.Surface0,
    surfaceContainerLow = KnotworkLight.Surface1,
    surfaceContainer = KnotworkLight.Surface2,
    surfaceContainerHigh = KnotworkLight.Surface3,
    surfaceContainerHighest = KnotworkLight.Surface4,
)

/**
 * Builds the Material3 [ColorScheme] for the dark Knotwork palette.
 *
 * The mapping mirrors [knotworkLightColorScheme]. The ground is
 * [KnotworkPalette.DeepSpace]. Primary stays [KnotworkPalette.Indigo].
 *
 * @return a freshly built [ColorScheme]; cheap to recompute on theme flips.
 */
fun knotworkDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = KnotworkPalette.Indigo,
    onPrimary = KnotworkDark.OnPrimary,
    primaryContainer = KnotworkDark.PrimaryContainer,
    onPrimaryContainer = KnotworkDark.OnPrimaryContainer,
    secondary = Color(0xFF8B8EF2),
    onSecondary = KnotworkDark.OnPrimary,
    secondaryContainer = KnotworkDark.Surface3,
    onSecondaryContainer = KnotworkDark.OnSurface,
    tertiary = KnotworkDark.RiskReadonly,
    onTertiary = KnotworkDark.OnPrimary,
    tertiaryContainer = KnotworkDark.TertiaryContainer,
    onTertiaryContainer = KnotworkDark.OnTertiaryContainer,
    error = KnotworkDark.RiskDestructive,
    onError = KnotworkDark.OnPrimary,
    errorContainer = Color(0xFF5A1B17),
    onErrorContainer = Color(0xFFFADCDA),
    background = KnotworkDark.Surface0,
    onBackground = KnotworkDark.OnSurface,
    surface = KnotworkDark.Surface0,
    onSurface = KnotworkDark.OnSurface,
    surfaceVariant = KnotworkDark.Surface2,
    onSurfaceVariant = KnotworkDark.OnSurface2,
    surfaceTint = KnotworkPalette.Indigo,
    inverseSurface = KnotworkDark.SurfaceInv,
    inverseOnSurface = Color(0xFF12141C),
    inversePrimary = KnotworkPalette.Indigo,
    outline = KnotworkDark.Outline,
    outlineVariant = KnotworkDark.Divider,
    scrim = Color(0x99000000),
    // Material3 expanded surface ramp (M3 1.1+): mirror the Knotwork dark
    // Surface0..4 ramp so tonal containers stay on-brand. `surfaceBright`
    // takes the lightest dark step (`Surface4`); `surfaceDim` the deepest.
    surfaceBright = KnotworkDark.Surface4,
    surfaceDim = KnotworkDark.Surface0,
    surfaceContainerLowest = KnotworkDark.Surface0,
    surfaceContainerLow = KnotworkDark.Surface1,
    surfaceContainer = KnotworkDark.Surface2,
    surfaceContainerHigh = KnotworkDark.Surface3,
    surfaceContainerHighest = KnotworkDark.Surface4,
)
