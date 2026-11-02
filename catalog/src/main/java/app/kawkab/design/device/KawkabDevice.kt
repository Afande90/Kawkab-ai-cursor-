package app.kawkab.design.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kawkab.design.R
import app.kawkab.design.theme.KnotworkTheme
import kotlinx.coroutines.delay
import java.util.Locale

private val BodyTop = Color(BODY_TOP_ARGB)
private val BodyBottom = Color(BODY_BOTTOM_ARGB)
private val Bezel = Color(BEZEL_ARGB)
private val Panel = Color(PANEL_ARGB)
private val Ink = Color(INK_ARGB)
private val Orange = Color(ORANGE_ARGB)
private val LcdFont = FontFamily(Font(R.font.vt323))
private val HeaderFont = FontFamily(Font(R.font.ibm_plex_mono))
private val ArabicFont = FontFamily(Font(R.font.noto_kufi_arabic))

/** One physical device, shared by F1–F8 and the owner's settings pages. */
@Composable
fun KawkabDevice(
    state: DeviceState,
    onHome: () -> Unit = {},
    onShots: () -> Unit = {},
    onGrowth: () -> Unit = {},
    onTalk: () -> Unit = {},
    onSettings: () -> Unit = {},
    onForget: (String) -> Unit = {},
    onVoice: () -> Unit = {},
    modifier: Modifier = Modifier,
    lcdContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BodyTop, BodyBottom)))
            .safeDrawingPadding().padding(
                horizontal = BODY_PADDING_DP.dp,
                vertical = LCD_RADIUS_DP.dp,
            ).testTag("pocket_device"),
        verticalArrangement = Arrangement.spacedBy(BEZEL_RADIUS_DP.dp),
    ) {
        Header(state.signal, onSettings)
        Box(
            Modifier.weight(
                1f,
            ).fillMaxWidth().clip(
                RoundedCornerShape(BEZEL_RADIUS_DP.dp),
            ).background(Bezel).padding(BEZEL_PADDING_DP.dp),
        ) {
            Column(
                Modifier.fillMaxSize().clip(RoundedCornerShape(LCD_RADIUS_DP.dp)).background(Panel)
                    .border(2.dp, Ink.copy(alpha = LCD_EDGE_ALPHA), RoundedCornerShape(LCD_RADIUS_DP.dp))
                    .padding(LCD_PADDING_DP.dp).verticalScroll(rememberScrollState()).testTag("lcd_${state.page.name}"),
                verticalArrangement = Arrangement.spacedBy(CONTENT_GAP_DP.dp),
            ) {
                if (lcdContent != null) lcdContent() else DeviceDisplay(state, onForget, onVoice)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP_DP.dp)) {
            DeviceKey("HOME", state.page !in listOf(DevicePage.SHOTS, DevicePage.GROWTH), onHome, Modifier.weight(1f))
            DeviceKey("SHOTS", state.page == DevicePage.SHOTS, onShots, Modifier.weight(1f))
            DeviceKey("GROWTH", state.page == DevicePage.GROWTH, onGrowth, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Grille(Modifier.weight(1f))
            val stopping = state.page == DevicePage.CALLING
            Box(
                Modifier.padding(
                    bottom = LCD_RADIUS_DP.dp,
                ).size(TALK_DIAMETER_DP.dp).background(Color(TALK_SHADOW_ARGB), CircleShape)
                    .padding(bottom = GRILLE_ROWS.dp).clip(CircleShape).background(if (stopping) Bezel else Orange)
                    .clickable(onClick = onTalk).testTag("talk_key"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (stopping) "STOP" else talkLabel(state.signal),
                    color = Color.White,
                    fontFamily = HeaderFont,
                    fontSize = CONTENT_GAP_DP.sp,
                )
            }
            Grille(Modifier.weight(1f))
        }
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
            Text("⋮", color = Color(HEADER_ARGB), fontSize = SPACER_DP.sp)
        }
    }
}

/** LCD text keeps spoken Arabic as spoken, with English labels outside its direction override. */
@Composable
fun LcdLine(text: String, modifier: Modifier = Modifier, spoken: Boolean = false, large: Boolean = false) {
    val arabic = spoken && text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC }
    Text(
        if (spoken) text else text.uppercase(Locale.ROOT),
        modifier = modifier,
        color = Ink,
        style = TextStyle(
            fontFamily = if (arabic) ArabicFont else LcdFont,
            fontSize = if (large) {
                COUNT_SP.sp
            } else if (arabic) {
                ARABIC_SP.sp
            } else {
                LCD_SP.sp
            },
            textDirection = if (arabic) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr,
        ),
    )
}

/** A tactile key with a physical shadow; all touch targets are at least 48 dp. */
@Composable
fun DeviceKey(label: String, pressed: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.height(
            KEY_HEIGHT_DP.dp,
        ).background(
            Color(KEY_SHADOW_ARGB),
            RoundedCornerShape(PIXEL_STEPS.dp),
        ).padding(bottom = if (pressed) 0.dp else KEY_SHADOW_DP.dp),
    ) {
        Box(
            Modifier.fillMaxSize().clip(
                RoundedCornerShape(GRILLE_ROWS.dp),
            ).background(if (pressed) Color(KEY_PRESSED_ARGB) else Color(KEY_ARGB))
                .clickable(onClick = onClick).padding(LCD_RADIUS_DP.dp),
            Alignment.Center,
        ) {
            Text(label, color = Ink, fontFamily = HeaderFont, fontSize = HEADER_SP.sp)
        }
    }
}

@Composable
private fun DeviceDisplay(state: DeviceState, onForget: (String) -> Unit, onVoice: () -> Unit) {
    when (state.page) {
        DevicePage.IDLE -> IdleDisplay(state)
        DevicePage.LISTENING -> {
            LcdLine("HEARING YOU")
            Waveform()
            LcdLine(state.heard.ifBlank { "..." }, spoken = true)
            LcdLine("I ACT WHEN YOU STOP TALKING")
        }
        DevicePage.WHICH -> {
            LcdLine("WHICH ${state.name.ifBlank { "CONTACT" }}?")
            state.choices.forEachIndexed { i, name -> LcdLine("${i + 1}. $name", spoken = true) }
            LcdLine("SAY 1 OR 2, I'LL REMEMBER")
        }
        DevicePage.CALLING -> {
            LcdLine("CALLING")
            LcdLine(state.name, spoken = true)
            LcdLine(state.number, spoken = true)
            if (state.usual) LcdLine("YOUR USUAL")
            LcdLine(state.seconds.toString(), large = true)
            LcdLine(blocks(state.seconds.coerceIn(0, COUNTDOWN_DIGITS), COUNTDOWN_DIGITS))
            LcdLine("SAY STOP OR TAP STOP")
        }
        DevicePage.TALK -> {
            LcdLine("YOU:")
            LcdLine(state.heard, spoken = true)
            LcdLine("KAWKAB:")
            LcdLine(state.reply, spoken = true)
            if (state.result.isNotBlank()) LcdLine("> ${state.result}")
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
            LcdLine(state.reply, spoken = true)
            DeviceKey("RECORD MY VOICE", onClick = onVoice)
        }
    }
}

@Composable
private fun IdleDisplay(state: DeviceState) {
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
    Spacer(Modifier.height(SPACER_DP.dp))
    if (state.result.isNotBlank()) LcdLine("> ${state.result} .... OK")
}

@Composable
private fun ShotsDisplay(shots: List<DeviceShot>) {
    LcdLine("SHOTS")
    if (shots.isEmpty()) LcdLine("NO SHOTS YET. SHARE A SCREENSHOT.")
    shots.forEach { shot ->
        Column(Modifier.fillMaxWidth().border(2.dp, Ink).padding(LCD_RADIUS_DP.dp)) {
            LcdLine(if (shot.needsAnswer) "? ${shot.title} ... SAY IT" else "[${shot.category}] ${shot.title}")
        }
    }
}

@Composable
private fun GrowthDisplay(state: DeviceState, onForget: (String) -> Unit, onVoice: () -> Unit) {
    LcdLine("WHAT I LEARNED THIS WEEK")
    LcdLine(state.accuracy?.let { "RIGHT FIRST TIME $it%" } ?: "NO SCORED DECISIONS YET")
    if (state.trend.isNotEmpty()) {
        LcdLine(
            state.trend.joinToString(" ") {
                "▁▂▃▄▅▆▇█"[(it.coerceIn(0, 100) * PIXEL_STEPS / 100)].toString()
            },
        )
    }
    state.learned.forEach { item ->
        LcdLine(item.text, spoken = true)
        DeviceKey("FORGET", onClick = { onForget(item.id) })
    }
    LcdLine("SAY 'FORGET ...' TO REMOVE")
    DeviceKey("MY VOICE", onClick = onVoice)
}

@Composable
private fun Waveform() {
    Canvas(Modifier.fillMaxWidth().height(WAVE_HEIGHT_DP.dp)) {
        val bars = WAVE_HEIGHTS.split(' ').map(String::toInt)
        bars.forEachIndexed { i, height ->
            val x = size.width * i / bars.size
            drawLine(Ink, Offset(x, size.height / 2 - height), Offset(x, size.height / 2 + height), WAVE_STROKE_PX)
        }
    }
}

@Composable
private fun Grille(modifier: Modifier) {
    Canvas(modifier.height(GRILLE_HEIGHT_DP.dp).padding(CONTENT_GAP_DP.dp)) {
        for (row in 0..GRILLE_X_DIVISOR) {
            for (column in 0..COUNTDOWN_DIGITS) {
                drawCircle(
                    Color(KEY_SHADOW_ARGB),
                    GRILLE_DOT_DP.dp.toPx(),
                    Offset(
                        size.width * (column + 1) / GRILLE_X_DIVISOR,
                        size.height * (row + 1) / PIXEL_STEPS,
                    ),
                )
            }
        }
    }
}

private fun blocks(count: Int, total: Int): String =
    "[" + "■".repeat(count.coerceIn(0, total)) + "□".repeat((total - count).coerceIn(0, total)) + "]"

/** Exact colours from the owner's Pocket LCD specification. */
fun signalColor(signal: DeviceSignal): Color = when (signal) {
    DeviceSignal.STANDBY -> Color(STANDBY_ARGB)
    DeviceSignal.LISTENING, DeviceSignal.SPEAKING -> Color(LISTEN_ARGB)
    DeviceSignal.ASKING -> Color(ASK_ARGB)
    DeviceSignal.ACTING -> Orange
}

private fun talkLabel(signal: DeviceSignal): String = when (signal) {
    DeviceSignal.STANDBY -> "HOLD TO TALK"
    DeviceSignal.LISTENING -> "HEARING YOU"
    DeviceSignal.SPEAKING -> "CUT IN"
    DeviceSignal.ASKING -> "JUST SAY IT"
    DeviceSignal.ACTING -> "MIC ON"
}

private const val WAVE_HEIGHTS = "2 4 8 5 12 18 9 25 17 7 14 28 16 10 5 8 3 2"
private const val BODY_TOP_ARGB = 0xFFD6D2C9
private const val BODY_BOTTOM_ARGB = 0xFFC7C3B9
private const val BEZEL_ARGB = 0xFF2A2925
private const val PANEL_ARGB = 0xFFA9B48A
private const val INK_ARGB = 0xFF1E2416
private const val ORANGE_ARGB = 0xFFE2611B
private const val TALK_SHADOW_ARGB = 0xFF9C3F0E
private const val HEADER_ARGB = 0xFF5E5A52
private const val KEY_SHADOW_ARGB = 0xFF9B978E
private const val KEY_PRESSED_ARGB = 0xFFBDB8AE
private const val KEY_ARGB = 0xFFE7E4DD
private const val STANDBY_ARGB = 0xFF6E6A62
private const val LISTEN_ARGB = 0xFF3FBF4A
private const val ASK_ARGB = 0xFFF2A51A
private const val COUNTDOWN_DIGITS = 3
private const val KEY_SHADOW_DP = 4
private const val GRILLE_X_DIVISOR = 5
private const val GRILLE_ROWS = 6
private const val PIXEL_STEPS = 7
private const val LCD_RADIUS_DP = 8
private const val WAKE_REPEATS = 10
private const val HEADER_SP = 11
private const val CONTENT_GAP_DP = 12
private const val BEZEL_PADDING_DP = 14
private const val LCD_PADDING_DP = 16
private const val ARABIC_SP = 17
private const val BEZEL_RADIUS_DP = 18
private const val BODY_PADDING_DP = 22
private const val SPACER_DP = 24
private const val LCD_SP = 25
private const val IDLE_SPACER_DP = 30
private const val MIN_TOUCH_DP = 48
private const val KEY_HEIGHT_DP = 54
private const val WAVE_HEIGHT_DP = 64
private const val GRILLE_HEIGHT_DP = 70
private const val COUNT_SP = 72
private const val TALK_DIAMETER_DP = 124
private const val CURSOR_PERIOD_MS = 600L
private const val WAVE_STROKE_PX = 6f
private const val LCD_EDGE_ALPHA = 0.18f
private const val HEADER_TRACKING = 1.76
private const val GRILLE_DOT_DP = 1.6
private const val EDGE_ALPHA = 0.2f

private const val SENTENCE_OFFSET = 9
