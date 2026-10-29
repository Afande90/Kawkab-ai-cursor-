package app.kawkab.stubmessenger

import android.content.ContentValues
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp

/**
 * The known compose screen for the stub messenger.
 *
 * The send control is tagged `send_button`, which the accessibility service
 * looks up as `app.kawkab.stubmessenger:id/send_button`. Its label is Send.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MessengerScreen(onSend = ::store)
        }
    }

    private fun store(contact: String, body: String) {
        val values = ContentValues().apply {
            put(InboxProvider.COLUMN_CONTACT, contact)
            put(InboxProvider.COLUMN_BODY, body)
        }
        contentResolver.insert(InboxProvider.CONTENT_URI, values)
    }
}

@Composable
private fun MessengerScreen(onSend: (String, String) -> Unit) {
    var contact by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    val sendLabel = stringResource(R.string.stub_send)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics { testTagsAsResourceId = true },
    ) {
        OutlinedTextField(
            value = contact,
            onValueChange = { contact = it },
            label = { Text(stringResource(R.string.stub_contact_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("contact_field"),
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text(stringResource(R.string.stub_message_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .testTag("message_field"),
        )
        Button(
            onClick = {
                if (body.isNotBlank()) {
                    onSend(contact.trim(), body.trim())
                    body = ""
                }
            },
            modifier = Modifier
                .padding(top = 12.dp)
                .testTag("send_button")
                .semantics { contentDescription = sendLabel },
        ) {
            Text(sendLabel)
        }
    }
}
