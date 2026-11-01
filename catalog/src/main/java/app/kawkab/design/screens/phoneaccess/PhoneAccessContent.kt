package app.kawkab.design.screens.phoneaccess

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kawkab.design.components.buttons.KnotworkPrimaryButton
import app.kawkab.design.components.buttons.KnotworkSecondaryButton
import app.kawkab.design.icons.AppIcons
import app.kawkab.design.theme.KnotworkTheme
import app.kawkab.design.tokens.KnotworkTextStyles

/** Which phone-access item a card is about; decides its icon (task 23). */
enum class PhoneAccessKind {
    /** Reading contacts. */
    Contacts,

    /** Placing calls. */
    Phone,

    /** Adding calendar events. */
    Calendar,

    /** Showing notifications. */
    Notifications,

    /** The microphone. */
    Microphone,

    /** The accessibility service. */
    ScreenControl,

    /** Running in the background. */
    Background,

    /** The digital assistant role. */
    Assistant,

    /** Listening for the wake word "Kawkab" (task 27). */
    WakeWord,

    /** Answering a confirmation card by voice (task 29). */
    VoiceConfirm,
}

/**
 * One card on the Phone access screen.
 *
 * @property kind The item, for its icon and test tag.
 * @property title For example "Contacts".
 * @property reason One line on why Kawkab needs it.
 * @property on Whether the phone already allows it.
 * @property statusLabel "On" or "Off" in the owner's language.
 * @property buttonLabel The one full-width button, such as "Allow" or "Open settings".
 * @property note An extra line under the card, such as the power-button step, or `null`.
 */
data class PhoneAccessCard(
    val kind: PhoneAccessKind,
    val title: String,
    val reason: String,
    val on: Boolean,
    val statusLabel: String,
    val buttonLabel: String,
    val note: String? = null,
)

/**
 * The Phone access screen (task 23).
 *
 * @property title The screen title.
 * @property progress For example "5 of 8 on".
 * @property cards One card per item.
 * @property doneLabel The button that leaves the screen.
 */
data class PhoneAccessViewState(
    val title: String,
    val progress: String,
    val cards: List<PhoneAccessCard>,
    val doneLabel: String,
)

/**
 * Phone access as design-system cards: a progress line, then one card per item with its icon,
 * reason, an On/Off chip (green or amber) and one full-width button.
 *
 * @param state What to show.
 * @param onCard Called with the card's kind when its button is tapped.
 * @param onDone Leaves the screen.
 */
@Composable
fun PhoneAccessContent(
    state: PhoneAccessViewState,
    onCard: (PhoneAccessKind) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp3),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(KnotworkTheme.spacing.sp4),
    ) {
        Text(text = state.title, style = KnotworkTextStyles.TitleLg, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = state.progress,
            style = KnotworkTextStyles.BodyBase,
            color = KnotworkTheme.extended.onSurfaceMuted,
            modifier = Modifier.testTag(PROGRESS_TAG),
        )
        state.cards.forEach { card -> AccessCard(card = card, onClick = { onCard(card.kind) }) }
        KnotworkSecondaryButton(text = state.doneLabel, onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AccessCard(card: PhoneAccessCard, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KnotworkTheme.spacing.sp2),
        modifier = Modifier
            .fillMaxWidth()
            .clip(KnotworkTheme.shapes.md)
            .background(KnotworkTheme.extended.surface1)
            .border(1.dp, KnotworkTheme.extended.divider, KnotworkTheme.shapes.md)
            .padding(KnotworkTheme.spacing.sp4)
            .testTag("$CARD_TAG_PREFIX${card.kind.name}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = iconOf(card.kind),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ICON_DP.dp),
            )
            Spacer(Modifier.size(KnotworkTheme.spacing.sp3))
            Text(
                text = card.title,
                style = KnotworkTextStyles.BodyBase.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            StatusChip(on = card.on, label = card.statusLabel)
        }
        Text(text = card.reason, style = KnotworkTextStyles.BodySm, color = KnotworkTheme.extended.onSurface2)
        card.note?.let {
            Text(text = it, style = KnotworkTextStyles.BodySm, color = KnotworkTheme.extended.onSurfaceMuted)
        }
        if (card.on) {
            KnotworkSecondaryButton(text = card.buttonLabel, onClick = onClick, modifier = Modifier.fillMaxWidth())
        } else {
            KnotworkPrimaryButton(text = card.buttonLabel, onClick = onClick, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StatusChip(on: Boolean, label: String) {
    val color = if (on) KnotworkTheme.extended.signalSuccess else KnotworkTheme.extended.signalWarn
    Text(
        text = label,
        style = KnotworkTextStyles.LabelSm,
        color = color,
        modifier = Modifier
            .clip(KnotworkTheme.shapes.sm)
            .border(1.dp, color, KnotworkTheme.shapes.sm)
            .padding(horizontal = KnotworkTheme.spacing.sp2, vertical = KnotworkTheme.spacing.sp1),
    )
}

private fun iconOf(kind: PhoneAccessKind): ImageVector = when (kind) {
    PhoneAccessKind.Contacts -> AppIcons.Bookmark
    PhoneAccessKind.Phone -> AppIcons.Send
    PhoneAccessKind.Calendar -> AppIcons.History
    PhoneAccessKind.Notifications -> AppIcons.Info
    PhoneAccessKind.Microphone -> AppIcons.Spark
    PhoneAccessKind.ScreenControl -> AppIcons.Eye
    PhoneAccessKind.Background -> AppIcons.Bolt
    PhoneAccessKind.Assistant -> AppIcons.Chat
    PhoneAccessKind.WakeWord -> AppIcons.Mic
    PhoneAccessKind.VoiceConfirm -> AppIcons.Chat
}

private const val ICON_DP = 20

/** Test tag of the progress line. */
const val PROGRESS_TAG: String = "phone_access_progress"

/** Test tag prefix of each card; the kind name follows. */
const val CARD_TAG_PREFIX: String = "phone_access_card_"
