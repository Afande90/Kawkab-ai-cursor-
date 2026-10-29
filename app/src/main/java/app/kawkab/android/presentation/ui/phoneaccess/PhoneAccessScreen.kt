package app.kawkab.android.presentation.ui.phoneaccess

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.kawkab.android.R
import app.kawkab.android.assistant.AssistantRoleRequest
import app.kawkab.android.domain.phoneaccess.MissingPermission
import app.kawkab.android.domain.phoneaccess.PhoneAccessItem
import app.kawkab.android.domain.phoneaccess.PhoneAccessStatus
import app.kawkab.design.components.chat.ChatContent
import app.kawkab.design.components.chat.ChatRole
import app.kawkab.design.screens.chat.ChatHomeMessageRow

/**
 * Shows each phone-access item, with a grant path when it is off.
 *
 * @param onDone Leaves the screen after the owner has seen the list.
 * @param requestPermission A permission to ask for as soon as the screen opens.
 */
@Composable
fun PhoneAccessScreen(onDone: () -> Unit, requestPermission: String? = null) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var assistantHeld by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        tick += 1
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        tick += 1
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val lines = remember(tick, lifecycleState, context) { AndroidPhoneAccess.lines(context) }
    LaunchedEffect(requestPermission) {
        if (!requestPermission.isNullOrBlank()) permissionLauncher.launch(requestPermission)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(all = 16.dp),
    ) {
        Text(stringResource(R.string.phone_access_title))
        lines.forEach { line ->
            Text(stringResource(reasonOf(line.item)))
            Text(stringResource(if (line.on) R.string.phone_access_on else R.string.phone_access_off))
            val permission = PhoneAccessStatus.permission(line.item)
            if (permission != null && !line.on) {
                TextButton(onClick = { permissionLauncher.launch(permission) }) {
                    Text(stringResource(R.string.phone_access_grant))
                }
            }
            if (line.item == PhoneAccessItem.ACCESSIBILITY) {
                Text(stringResource(R.string.phone_access_restricted_title))
                Image(
                    painter = painterResource(R.drawable.phone_access_menu),
                    contentDescription = stringResource(R.string.phone_access_menu_cd),
                )
                Text(stringResource(R.string.phone_access_restricted_body))
                TextButton(onClick = { openSafely(context, AndroidPhoneAccess.accessibilitySettings()) }) {
                    Text(stringResource(R.string.phone_access_accessibility_open))
                }
            }
            if (line.item == PhoneAccessItem.BATTERY) {
                Text(stringResource(R.string.phone_access_nothing))
                TextButton(onClick = { openSafely(context, AndroidPhoneAccess.batterySettings(context)) }) {
                    Text(stringResource(R.string.phone_access_battery_open))
                }
            }
        }
        Text(stringResource(R.string.phone_access_assistant_title))
        AssistantRoleRequest.steps(context).forEach { step -> Text(step) }
        if (assistantHeld) {
            Text(stringResource(R.string.phone_access_assistant_held))
        } else {
            TextButton(
                onClick = {
                    val intent = AssistantRoleRequest.create(context)
                    if (intent == null) {
                        assistantHeld = true
                    } else {
                        roleLauncher.launch(intent)
                    }
                },
            ) {
                Text(stringResource(R.string.phone_access_assistant_open))
            }
        }
        TextButton(onClick = onDone) { Text(stringResource(R.string.phone_access_done)) }
    }
}

/**
 * The card under a command that stopped for a missing grant. Grant asks Android. The list opens Phone access.
 */
@Composable
fun MissingPermissionPrompt(permission: String, onOpenList: () -> Unit, modifier: Modifier = Modifier) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(all = 12.dp),
    ) {
        Text(stringResource(R.string.phone_access_card))
        TextButton(onClick = { launcher.launch(permission) }) {
            Text(stringResource(R.string.phone_access_grant))
        }
        TextButton(onClick = onOpenList) { Text(stringResource(R.string.phone_access_open)) }
    }
}

/**
 * The permission named by the latest assistant line, when that line is a missing-permission card.
 */
fun permissionInThread(messages: List<ChatHomeMessageRow>): String? {
    val row = messages.lastOrNull { it.role == ChatRole.Assistant || it.role == ChatRole.Tool } ?: return null
    val text = when (val body = row.content) {
        is ChatContent.Markdown -> body.source
        is ChatContent.Text -> body.text
        else -> return null
    }
    return MissingPermission.permissionIn(text)
}

private fun reasonOf(item: PhoneAccessItem): Int = when (item) {
    PhoneAccessItem.CONTACTS -> R.string.phone_access_reason_contacts
    PhoneAccessItem.PHONE -> R.string.phone_access_reason_phone
    PhoneAccessItem.CALENDAR -> R.string.phone_access_reason_calendar
    PhoneAccessItem.NOTIFICATIONS -> R.string.phone_access_reason_notifications
    PhoneAccessItem.MICROPHONE -> R.string.phone_access_reason_microphone
    PhoneAccessItem.ACCESSIBILITY -> R.string.phone_access_reason_accessibility
    PhoneAccessItem.BATTERY -> R.string.phone_access_reason_battery
}

private fun openSafely(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}
