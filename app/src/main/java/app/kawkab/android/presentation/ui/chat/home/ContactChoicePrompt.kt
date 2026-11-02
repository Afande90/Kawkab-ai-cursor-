package app.kawkab.android.presentation.ui.chat.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kawkab.android.R
import app.kawkab.android.hands.ContactMatch

/**
 * Numbered contact names for a spoken choice (vision rule 2). The conversation driver reads the
 * question and sends the spoken answer through the pending command router.
 *
 * @param choices The contacts, at most five.
 * @param onCancel Drops the pending command.
 */
@Composable
fun ContactChoicePrompt(choices: List<ContactMatch>, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(all = 12.dp)
            .testTag(CONTACT_CHOICE_TAG),
    ) {
        Text(stringResource(R.string.contact_choice_title))
        choices.forEachIndexed { index, match ->
            Text("${index + 1}. ${match.name}")
        }
        Text("SAY THE NUMBER OR NAME. I WILL REMEMBER.")
        TextButton(onClick = onCancel) { Text(stringResource(R.string.contact_choice_cancel)) }
    }
}

/** Test tag of the prompt. */
const val CONTACT_CHOICE_TAG = "contact_choice"

/** Test tag of each chip. */
const val CONTACT_CHIP_TAG = "contact_choice_chip"
