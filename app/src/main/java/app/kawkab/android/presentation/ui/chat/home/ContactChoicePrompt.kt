package app.kawkab.android.presentation.ui.chat.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kawkab.android.R
import app.kawkab.android.hands.ContactChip
import app.kawkab.android.hands.ContactMatch

/**
 * Tappable contact chips for a call or text waiting on a pick (task 23j). Each chip shows the
 * name, the last four digits and the label, never the whole number.
 *
 * @param choices The contacts, at most five.
 * @param onPick Completes the pending command with the tapped contact.
 * @param onCancel Drops the pending command.
 */
@Composable
fun ContactChoicePrompt(
    choices: List<ContactMatch>,
    onPick: (ContactMatch) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(all = 12.dp)
            .testTag(CONTACT_CHOICE_TAG),
    ) {
        Text(stringResource(R.string.contact_choice_title))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { match ->
                SuggestionChip(
                    onClick = { onPick(match) },
                    label = { Text(ContactChip.text(match)) },
                    modifier = Modifier.testTag(CONTACT_CHIP_TAG),
                )
            }
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.contact_choice_cancel)) }
    }
}

/** Test tag of the prompt. */
const val CONTACT_CHOICE_TAG = "contact_choice"

/** Test tag of each chip. */
const val CONTACT_CHIP_TAG = "contact_choice_chip"
