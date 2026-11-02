package app.kawkab.design.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kawkab.design.R
import app.kawkab.design.theme.KnotworkTheme
import kotlinx.coroutines.delay
import java.util.Locale

private val LcdFont = FontFamily(Font(R.font.vt323))
private val HeaderFont = FontFamily(Font(R.font.ibm_plex_mono))
private val ArabicFont = FontFamily(Font(R.font.noto_kufi_arabic))
private val Ink = Color(INK_ARGB)
private val Bezel = Color(BEZEL_ARGB)

/** One physical device. Every LCD is 400 dp high; each view owns a bottom-anchored final block. */
@Composable
fun KawkabDevice(
    state: DeviceState,
    modifier: Modifier = Modifier,
    onHome: () -> Unit = {},
    onShots: () -> Unit = {},
    onGrowth: () -> Unit = {},
    onTalk: () -> Unit = {},
    onSettings: () -> Unit = {},
    onForget: (String) -> Unit = {},
    onVoice: () -> Unit = {},
    lcdContent: (@Composable () -> Unit)? = null,
    lcdFooter: (@Composable () -> Unit)? = null,
    onLcdTap: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(BODY_TOP_ARGB), Color(BODY_BOTTOM_ARGB))))
            .safeDrawingPadding().verticalScroll(
                rememberScrollState(),
            ).padding(horizontal = BODY_PADDING_DP.dp, vertical = 4.dp)
            .testTag("pocket_device"),
        verticalArrangement = Arrangement.spacedBy(GAP_DP.dp),
    ) {
        Header(state.signal, onSettings)
        Box(
            Modifier.height((LCD_HEIGHT_DP + 2 * BEZEL_PADDING_DP).dp).fillMaxWidth()
                .clip(RoundedCornerShape(BEZEL_RADIUS_DP.dp)).background(Bezel).padding(BEZEL_PADDING_DP.dp),
        ) {
            Column(
                Modifier.fillMaxSize().clip(RoundedCornerShape(LCD_RADIUS_DP.dp)).background(Color(PANEL_ARGB))
                    .border(2.dp, Ink.copy(alpha = EDGE_ALPHA), RoundedCornerShape(LCD_RADIUS_DP.dp))
                    .innerShadow(
                        RoundedCornerShape(LCD_RADIUS_DP.dp),
                        Shadow(
                            radius = KEY_SHADOW_DP.dp,
                            color = Ink.copy(alpha = EDGE_ALPHA),
                            offset = DpOffset(2.dp, 2.dp),
                        ),
                    )
                    .testTag("lcd_panel").pointerInput(state.page, onLcdTap) {
                        detectTapGestures { if (state.page == DevicePage.CALLING) onTalk() else onLcdTap() }
                    }.padding(LCD_PADDING_DP.dp).testTag("lcd_${state.page.name}"),
                verticalArrangement = Arrangement.spacedBy(GAP_DP.dp),
            ) {
                if (lcdContent != null) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(GAP_DP.dp),
                    ) {
                        lcdContent()
                    }
                } else {
                    lcdFooter?.invoke()
                    DeviceDisplay(state, onForget, onVoice)
                }
            }
        }
        if (state.page == DevicePage.CALLING) {
            Box(Modifier.fillMaxWidth().testTag("calling_hint"), contentAlignment = Alignment.Center) {
                Text(
                    "SAY \"STOP\" — OR PRESS",
                    color = Color(HEADER_ARGB),
                    fontFamily = HeaderFont,
                    fontSize = HINT_SP.sp,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP_DP.dp)) {
            DeviceKey(
                "HOME",
                onHome,
                Modifier.weight(1f),
                pressed =
                state.page !in listOf(DevicePage.SHOTS, DevicePage.GROWTH),
            )
            DeviceKey("SHOTS", onShots, Modifier.weight(1f), pressed = state.page == DevicePage.SHOTS)
            DeviceKey("GROWTH", onGrowth, Modifier.weight(1f), pressed = state.page == DevicePage.GROWTH)
        }
        TalkKey(state, onTalk)
    }
}

@Composable
private fun Header(signal: DeviceSignal, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "KAWKAB K-1",
            color = Color(HEADER_ARGB),
            fontFamily = HeaderFont,
            fontSize = HEADER_SP.sp,
            letterSpacing = HEADER_TRACKING.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            signal.name,
            color = Color(HEADER_ARGB),
            fontFamily = HeaderFont,
            fontSize = HEADER_SP.sp,
            letterSpacing = HEADER_TRACKING.sp,
        )
        Spacer(Modifier.width(LCD_RADIUS_DP.dp))
        Box(Modifier.size(LCD_RADIUS_DP.dp).background(signalColor(signal), CircleShape).testTag("status_led"))
        Box(
            Modifier.sizeIn(minWidth = MIN_TOUCH_DP.dp, minHeight = MIN_TOUCH_DP.dp).clickable(onClick = onSettings),
            Alignment.Center,
        ) {
            Text("⋮", color = Color(HEADER_ARGB), fontSize = MENU_SP.sp)
        }
    }
}

@Composable
private fun TalkKey(state: DeviceState, onTalk: () -> Unit) {
    val stopping = state.page == DevicePage.CALLING
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Grille(Modifier.weight(1f))
        Box(Modifier.size(TALK_DIAMETER_DP.dp, (TALK_DIAMETER_DP + TALK_SHADOW_DP).dp)) {
            Box(
                Modifier.offset(y = TALK_SHADOW_DP.dp).size(TALK_DIAMETER_DP.dp)
                    .background(
                        Color(if (stopping) STOP_SHADOW_ARGB else TALK_SHADOW_ARGB),
                        CircleShape,
                    ).testTag("talk_shadow"),
            )
            Box(
                Modifier.size(
                    TALK_DIAMETER_DP.dp,
                ).clip(CircleShape).background(if (stopping) Bezel else Color(ORANGE_ARGB))
                    .clickable(onClick = onTalk).testTag("talk_key"),
                Alignment.Center,
            ) {
                Text(
                    if (stopping) "STOP" else talkLabel(state.signal),
                    color = Color(STOP_TEXT_ARGB),
                    fontFamily = HeaderFont,
                    fontSize = if (stopping) STOP_SP.sp else GAP_DP.sp,
                )
            }
        }
        Grille(Modifier.weight(1f))
    }
}

/** Spoken Arabic keeps its glyphs and direction; all other app copy uses the LCD font and uppercase. */
@Composable
fun LcdLine(
    text: String,
    modifier: Modifier = Modifier,
    spoken: Boolean = false,
    large: Boolean = false,
    size: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val arabic = spoken && text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC }
    Text(
        if (spoken) text else text.uppercase(Locale.ROOT),
        modifier = modifier,
        color = Ink,
        style = TextStyle(
            fontFamily = if (arabic) ArabicFont else LcdFont,
            fontSize = if (size !=
                TextUnit.Unspecified
            ) {
                size
            } else if (large) {
                COUNT_SP.sp
            } else if (arabic) {
                ARABIC_SP.sp
            } else {
                LCD_SP.sp
            },
            lineHeight = lineHeight,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            textDirection = if (arabic) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr,
        ),
    )
}

/** Tactile navigation and settings key, with a minimum 48 dp target. */
@Composable
fun DeviceKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, pressed: Boolean = false) {
    Box(
        modifier.height(KEY_HEIGHT_DP.dp).background(Color(KEY_SHADOW_ARGB), RoundedCornerShape(KEY_RADIUS_DP.dp))
            .padding(bottom = if (pressed) 0.dp else KEY_SHADOW_DP.dp),
    ) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(KEY_RADIUS_DP.dp))
                .background(
                    Color(if (pressed) KEY_PRESSED_ARGB else KEY_ARGB),
                ).then(
                    if (pressed) {
                        Modifier.innerShadow(
                            RoundedCornerShape(KEY_RADIUS_DP.dp),
                            Shadow(
                                radius = KEY_SHADOW_DP.dp,
                                color = Ink.copy(alpha = EDGE_ALPHA),
                                offset = DpOffset(2.dp, 2.dp),
                            ),
                        )
                    } else {
                        Modifier
                    },
                )
                .semantics { selected = pressed }.clickable(onClick = onClick).padding(LCD_RADIUS_DP.dp),
            Alignment.Center,
        ) {
            Text(label, color = Ink, fontFamily = HeaderFont, fontSize = HEADER_SP.sp)
        }
    }
}

@Composable
private fun ColumnScope.DeviceDisplay(state: DeviceState, onForget: (String) -> Unit, onVoice: () -> Unit) {
    when (state.page) {
        DevicePage.IDLE -> IdleDisplay(state)
        DevicePage.LISTENING -> {
            LcdLine("HEARING YOU")
            Waveform()
            LcdLine(state.heard.ifBlank { "..." }, spoken = true)
            Spacer(Modifier.weight(1f))
            LcdLine("I ACT WHEN YOU STOP TALKING", Modifier.testTag("lcd_bottom"))
        }
        DevicePage.WHICH -> {
            LcdLine("WHICH ${state.name.ifBlank { "CONTACT" }}?")
            state.choices.forEachIndexed { i, name -> LcdLine("${i + 1}. $name", spoken = true) }
            Spacer(Modifier.weight(1f))
            LcdLine("SAY 1 OR 2, I'LL REMEMBER", Modifier.testTag("lcd_bottom"))
        }
        DevicePage.CALLING -> CallingDisplay(state)
        DevicePage.TALK -> {
            LcdLine("YOU:")
            LcdLine(state.heard, spoken = true)
            LcdLine("KAWKAB:")
            LcdLine(state.reply, spoken = true)
            Spacer(Modifier.weight(1f))
            LcdLine("> ${state.result.ifBlank { "..." }}", Modifier.testTag("lcd_bottom"))
        }
        DevicePage.SHOTS -> ShotsDisplay(state.shots)
        DevicePage.GROWTH -> GrowthDisplay(state, onForget, onVoice)
        DevicePage.ENROLL -> {
            LcdLine("MY VOICE")
            LcdLine(
                if (state.enrolled <
                    WAKE_REPEATS
                ) {
                    "SAY 'KAWKAB'"
                } else if (state.enrolled ==
                    WAKE_REPEATS
                ) {
                    "SAY 'THIS IS MY PHONE'"
                } else {
                    "SAY 'I SPEAK AND YOU LISTEN'"
                },
            )
            LcdLine(state.reply, spoken = true)
            Spacer(Modifier.weight(1f))
            Column(Modifier.testTag("lcd_bottom"), verticalArrangement = Arrangement.spacedBy(GAP_DP.dp)) {
                LcdLine(blocks(state.enrolled.coerceAtMost(WAKE_REPEATS), WAKE_REPEATS))
                LcdLine(
                    if (state.enrolled <
                        WAKE_REPEATS
                    ) {
                        "${state.enrolled}/10"
                    } else {
                        "SENTENCE ${state.enrolled - SENTENCE_OFFSET}/2"
                    },
                )
                LcdLine("ONLY YOU CAN WAKE ME")
                DeviceKey("RECORD MY VOICE", onClick = onVoice)
            }
        }
    }
}

@Composable
private fun ColumnScope.CallingDisplay(state: DeviceState) {
    LcdLine(
        "HEARD: ${state.heard.uppercase(Locale.ROOT)}",
        Modifier.testTag("calling_heard"),
        spoken = true,
        size = CALL_DETAIL_SP.sp,
    )
    Column(verticalArrangement = Arrangement.spacedBy(CALL_STACK_GAP_DP.dp)) {
        LcdLine(state.actionLabel, Modifier.testTag("calling_title"), size = CALL_TITLE_SP.sp)
        LcdLine(
            (
                if (state.actionLabel ==
                    "CALLING"
                ) {
                    state.name
                } else {
                    state.name.ifBlank { state.heard }
                }
                ).uppercase(Locale.ROOT),
            Modifier.testTag("calling_name"),
            spoken = true,
            size = CALL_NAME_SP.sp,
        )
        val ending = state.number.filter { it.isDigit() }.takeLast(PHONE_LAST_DIGITS)
        LcdLine(
            if (state.actionLabel ==
                "CALLING"
            ) {
                "MOB ..$ending${if (state.usual) "  (YOUR USUAL)" else ""}"
            } else {
                "SAY STOP TO CANCEL"
            },
            Modifier.testTag("calling_number"),
            size = CALL_DETAIL_SP.sp,
        )
    }
    Spacer(Modifier.weight(1f))
    Column(Modifier.fillMaxWidth().testTag("lcd_bottom"), horizontalAlignment = Alignment.End) {
        LcdLine(
            state.seconds.toString(),
            // Single-line Text otherwise keeps the full font height despite lineHeight.
            Modifier.testTag("countdown_digit").height(with(LocalDensity.current) { CALL_DIGIT_LINE_SP.sp.toDp() }),
            size = CALL_DIGIT_SP.sp,
            lineHeight = CALL_DIGIT_LINE_SP.sp,
        )
        val filled = (
            (
                COUNTDOWN_DIGITS - state.seconds.coerceIn(
                    0,
                    COUNTDOWN_DIGITS,
                ) + 1
                ) * COUNTDOWN_DIGITS
            ).coerceAtMost(PROGRESS_BLOCKS)
        LcdLine(blocks(filled, PROGRESS_BLOCKS), Modifier.testTag("calling_progress"), size = CALL_PROGRESS_SP.sp)
    }
}

@Composable
private fun ColumnScope.IdleDisplay(state: DeviceState) {
    val reducedMotion = KnotworkTheme.a11y.reducedMotion()
    var cursor by remember { mutableStateOf(true) }
    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) {
            while (true) {
                delay(CURSOR_PERIOD_MS)
                cursor = !cursor
            }
        }
    }
    LcdLine("${state.date}  ${state.battery}")
    Spacer(Modifier.height(IDLE_SPACER_DP.dp))
    LcdLine("SAY KAWKAB${if (cursor) "_" else " "}")
    Spacer(Modifier.weight(1f))
    LcdLine("> ${state.result.ifBlank { "READY" }} .... OK", Modifier.testTag("lcd_bottom"))
}

@Composable
private fun ColumnScope.ShotsDisplay(shots: List<DeviceShot>) {
    LcdLine("SHOTS")
    val review = shots.lastOrNull { it.needsAnswer }
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(GAP_DP.dp),
    ) {
        if (shots.isEmpty()) LcdLine("NO SHOTS YET. SHARE A SCREENSHOT.")
        shots.filter { it != review }.forEach { shot -> ShotBox(shot) }
    }
    if (review != null) {
        Box(Modifier.testTag("lcd_bottom")) { ShotBox(review) }
    } else {
        LcdLine(if (shots.isEmpty()) "> NO SHOTS YET" else "> ALL FILED", Modifier.testTag("lcd_bottom"))
    }
}

@Composable
private fun ShotBox(shot: DeviceShot) {
    Column(Modifier.fillMaxWidth().border(2.dp, Ink).padding(LCD_RADIUS_DP.dp)) {
        LcdLine(if (shot.needsAnswer) "? ${shot.title} ... SAY IT" else "[${shot.category}] ${shot.title}")
    }
}

@Composable
private fun ColumnScope.GrowthDisplay(state: DeviceState, onForget: (String) -> Unit, onVoice: () -> Unit) {
    LcdLine("WHAT I LEARNED THIS WEEK")
    LcdLine(state.accuracy?.let { "RIGHT FIRST TIME $it%" } ?: "NO SCORED DECISIONS YET")
    if (state.trend.isNotEmpty()) {
        LcdLine(
            state.trend.joinToString(" ") {
                "▁▂▃▄▅▆▇█"[
                    it.coerceIn(0, 100) * PIXEL_STEPS /
                        100,
                ].toString()
            },
        )
    }
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(GAP_DP.dp),
    ) {
        state.learned.forEach { item ->
            LcdLine(item.text, spoken = true)
            DeviceKey("FORGET", onClick = { onForget(item.id) })
        }
    }
    Column(Modifier.testTag("lcd_bottom"), verticalArrangement = Arrangement.spacedBy(GAP_DP.dp)) {
        LcdLine("SAY 'FORGET ...' TO REMOVE")
        DeviceKey("MY VOICE", onClick = onVoice)
    }
}

@Composable
private fun Waveform() {
    Canvas(Modifier.fillMaxWidth().height(WAVE_HEIGHT_DP.dp)) {
        WAVE_HEIGHTS.split(' ').map(String::toInt).forEachIndexed { i, height ->
            val x = size.width * i / WAVE_BARS
            drawLine(Ink, Offset(x, size.height / 2 - height), Offset(x, size.height / 2 + height), WAVE_STROKE_PX)
        }
    }
}

@Composable
private fun Grille(modifier: Modifier) {
    Canvas(modifier.height(GRILLE_HEIGHT_DP.dp).padding(GAP_DP.dp)) {
        for (row in 0..GRILLE_ROWS) {
            for (column in 0..COUNTDOWN_DIGITS) {
                drawCircle(
                    Color(KEY_SHADOW_ARGB),
                    GRILLE_DOT_DP.dp.toPx(),
                    Offset(
                        size.width * (column + 1) / GRILLE_DIVISOR,
                        size.height * (row + 1) / PIXEL_STEPS,
                    ),
                )
            }
        }
    }
}

private fun blocks(count: Int, total: Int): String =
    "[" + "■".repeat(count.coerceIn(0, total)) + "□".repeat((total - count).coerceIn(0, total)) + "]"

/** Exact physical LED colours; Glyph receives the same semantic state. */
fun signalColor(signal: DeviceSignal): Color = Color(
    when (signal) {
        DeviceSignal.STANDBY -> STANDBY_ARGB
        DeviceSignal.LISTENING, DeviceSignal.SPEAKING -> LISTEN_ARGB
        DeviceSignal.ASKING -> ASK_ARGB
        DeviceSignal.ACTING -> ORANGE_ARGB
    },
)

private fun talkLabel(signal: DeviceSignal): String = when (signal) {
    DeviceSignal.STANDBY -> "HOLD TO TALK"
    DeviceSignal.LISTENING -> "HEARING YOU"
    DeviceSignal.SPEAKING -> "CUT IN"
    DeviceSignal.ASKING -> "JUST SAY IT"
    DeviceSignal.ACTING -> "MIC ON"
}

private const val BODY_TOP_ARGB = 0xFFD6D2C9
private const val BODY_BOTTOM_ARGB = 0xFFC7C3B9
private const val BEZEL_ARGB = 0xFF2A2925
private const val PANEL_ARGB = 0xFFA9B48A
private const val INK_ARGB = 0xFF1E2416
private const val ORANGE_ARGB = 0xFFE2611B
private const val TALK_SHADOW_ARGB = 0xFF9C3F0E
private const val STOP_SHADOW_ARGB = 0xFF0E0D0B
private const val STOP_TEXT_ARGB = 0xFFF0EDE6
private const val HEADER_ARGB = 0xFF5E5A52
private const val KEY_SHADOW_ARGB = 0xFF9B978E
private const val KEY_PRESSED_ARGB = 0xFFBDB8AE
private const val KEY_ARGB = 0xFFE7E4DD
private const val STANDBY_ARGB = 0xFF6E6A62
private const val LISTEN_ARGB = 0xFF3FBF4A
private const val ASK_ARGB = 0xFFF2A51A
private const val LCD_HEIGHT_DP = 400
private const val BODY_PADDING_DP = 22
private const val BEZEL_PADDING_DP = 14
private const val BEZEL_RADIUS_DP = 18
private const val LCD_RADIUS_DP = 8
private const val LCD_PADDING_DP = 16
private const val GAP_DP = 12
private const val HEADER_SP = 11
private const val HEADER_TRACKING = 1.76
private const val MENU_SP = 24
private const val HINT_SP = 13
private const val MIN_TOUCH_DP = 48
private const val TALK_DIAMETER_DP = 124
private const val TALK_SHADOW_DP = 8
private const val STOP_SP = 15
private const val KEY_HEIGHT_DP = 54
private const val KEY_RADIUS_DP = 6
private const val KEY_SHADOW_DP = 4
private const val EDGE_ALPHA = 0.18f
private const val COUNT_SP = 72
private const val ARABIC_SP = 17
private const val LCD_SP = 25
private const val CALL_DETAIL_SP = 22
private const val CALL_TITLE_SP = 46
private const val CALL_NAME_SP = 32
private const val CALL_STACK_GAP_DP = 6
private const val CALL_DIGIT_SP = 150
private const val CALL_DIGIT_LINE_SP = 127.5
private const val CALL_PROGRESS_SP = 26
private const val PHONE_LAST_DIGITS = 4
private const val COUNTDOWN_DIGITS = 3
private const val PROGRESS_BLOCKS = 9
private const val WAKE_REPEATS = 10
private const val SENTENCE_OFFSET = 9
private const val CURSOR_PERIOD_MS = 600L
private const val IDLE_SPACER_DP = 30
private const val WAVE_HEIGHT_DP = 64
private const val WAVE_STROKE_PX = 6f
private const val WAVE_BARS = 18
private const val GRILLE_HEIGHT_DP = 70
private const val GRILLE_ROWS = 5
private const val GRILLE_DIVISOR = 5
private const val PIXEL_STEPS = 7
private const val GRILLE_DOT_DP = 1.6
private const val WAVE_HEIGHTS = "2 4 8 5 12 18 9 25 17 7 14 28 16 10 5 8 3 2"
