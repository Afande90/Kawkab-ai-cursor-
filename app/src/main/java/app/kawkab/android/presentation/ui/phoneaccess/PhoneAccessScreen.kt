package app.kawkab.android.presentation.ui.phoneaccess

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import app.kawkab.android.R
import app.kawkab.android.assistant.AssistantRoleRequest
import app.kawkab.android.domain.phoneaccess.GrantRoute
import app.kawkab.android.domain.phoneaccess.MissingPermission
import app.kawkab.android.domain.phoneaccess.PhoneAccessItem
import app.kawkab.android.domain.phoneaccess.PhoneAccessLine
import app.kawkab.android.domain.phoneaccess.PhoneAccessStatus
import app.kawkab.android.voice.VoiceConfirmSwitch
import app.kawkab.android.voice.WakeWordSwitch
import app.kawkab.design.components.chat.ChatContent
import app.kawkab.design.components.chat.ChatRole
import app.kawkab.design.components.dialogs.ConfirmDialog
import app.kawkab.design.components.dialogs.ConfirmDialogUi
import app.kawkab.design.screens.chat.ChatHomeMessageRow
import app.kawkab.design.screens.phoneaccess.PhoneAccessCard
import app.kawkab.design.screens.phoneaccess.PhoneAccessContent
import app.kawkab.design.screens.phoneaccess.PhoneAccessKind
import app.kawkab.design.screens.phoneaccess.PhoneAccessViewState

/**
 * Phone access (task 23): a progress line ("5 of 8 on") and one design-system card per item,
 * each with its reason, an On/Off chip and one full-width button. The status is read again
 * whenever the owner comes back from Android settings.
 *
 * @param onDone Leaves the screen after the owner has seen the list.
 * @param requestPermission A permission to ask for as soon as the screen opens.
 */
@Composable
fun PhoneAccessScreen(onDone: () -> Unit, requestPermission: String? = null) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var voiceWarning by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tick += 1
        // Android will not ask again: send the owner to App info instead of doing nothing (task 23j).
        if (!granted) openSafely(context, appInfo(context))
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        tick += 1
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val lines = remember(tick, lifecycleState, context) { AndroidPhoneAccess.lines(context) }
    LaunchedEffect(requestPermission) {
        if (!requestPermission.isNullOrBlank()) permissionLauncher.launch(requestPermission)
    }
    val state = PhoneAccessViewState(
        title = stringResource(R.string.phone_access_title),
        progress = PhoneAccessStatus.summary(lines).let { (on, total) ->
            stringResource(R.string.phone_access_progress, on, total)
        },
        cards = lines.map { line -> card(line) },
        doneLabel = stringResource(R.string.phone_access_done),
    )
    PhoneAccessContent(
        state = state,
        onCard = { kind ->
            val item = itemOf(kind)
            val permission = PhoneAccessStatus.permission(item)
            val on = lines.firstOrNull { it.item == item }?.on == true
            when {
                permission != null && on -> openSafely(context, appInfo(context))
                permission != null -> permissionLauncher.launch(permission)
                item == PhoneAccessItem.ACCESSIBILITY -> openSafely(context, AndroidPhoneAccess.accessibilitySettings())
                item == PhoneAccessItem.BATTERY -> openSafely(context, AndroidPhoneAccess.batterySettings(context))
                item == PhoneAccessItem.VOICE_CONFIRM -> {
                    if (on) {
                        VoiceConfirmSwitch.set(context, false)
                        tick += 1
                    } else {
                        voiceWarning = true
                    }
                }
                item == PhoneAccessItem.WAKE_WORD -> {
                    if (micOn(lines)) {
                        WakeWordSwitch.set(context, !on)
                        tick += 1
                    } else {
                        permissionLauncher.launch(MissingPermission.RECORD_AUDIO)
                    }
                }
                else -> AssistantRoleRequest.create(context)?.let(roleLauncher::launch)
                    ?: openSafely(context, AssistantRoleRequest.settingsFallback(context))
            }
        },
        onDone = onDone,
        modifier = Modifier.safeDrawingPadding(),
    )
    if (voiceWarning) {
        VoiceConfirmWarning(
            onEnable = {
                VoiceConfirmSwitch.set(context, true)
                voiceWarning = false
                tick += 1
            },
            onCancel = { voiceWarning = false },
        )
    }
}

/** The warning of spec §0.4.3, shown before "Confirm by voice" turns on (task 29). */
@Composable
private fun VoiceConfirmWarning(onEnable: () -> Unit, onCancel: () -> Unit) {
    ConfirmDialog(
        ui = ConfirmDialogUi(
            title = stringResource(R.string.voice_confirm_warning_title),
            body = stringResource(R.string.voice_confirm_warning_body),
            confirmLabel = stringResource(R.string.voice_confirm_warning_enable),
            cancelLabel = stringResource(R.string.voice_confirm_warning_cancel),
            destructive = true,
        ),
        onConfirm = onEnable,
        onDismiss = onCancel,
    )
}

@Composable
private fun card(line: PhoneAccessLine): PhoneAccessCard = PhoneAccessCard(
    kind = kindOf(line.item),
    title = stringResource(titleOf(line.item)),
    reason = stringResource(reasonOf(line.item)),
    on = line.on,
    statusLabel = stringResource(if (line.on) R.string.phone_access_on else R.string.phone_access_off),
    buttonLabel = stringResource(buttonOf(line.item, line.on)),
    note = when (line.item) {
        PhoneAccessItem.ACCESSIBILITY -> stringResource(R.string.phone_access_restricted_body).takeUnless { line.on }
        PhoneAccessItem.BATTERY -> stringResource(R.string.phone_access_nothing)
        PhoneAccessItem.ASSISTANT ->
            stringResource(R.string.phone_access_power_button) + "\n" +
                stringResource(R.string.phone_access_assistant_fallback)
        PhoneAccessItem.WAKE_WORD -> wakeNote(line.on)
        PhoneAccessItem.VOICE_CONFIRM -> stringResource(R.string.voice_confirm_note)
        else -> null
    },
)

private fun micOn(lines: List<PhoneAccessLine>): Boolean =
    lines.firstOrNull { it.item == PhoneAccessItem.MICROPHONE }?.on == true

/** The wake word card's counters: wakes and battery per hour since it was turned on (task 27). */
@Composable
private fun wakeNote(on: Boolean): String {
    val context = LocalContext.current
    val stats = WakeWordSwitch.stats(context)
    WakeWordSwitch.failure(context)?.let { return stringResource(R.string.startup_wake_failed, it) }
    if (!on || stats == null) return stringResource(R.string.wake_note_off)
    val now = System.currentTimeMillis()
    val perHour = stats.wakesPerHour(now)
        ?: return stringResource(R.string.wake_note_starting)
    val charging = WakeWordSwitch.charged(context) || WakeWordSwitch.isCharging(context)
    val battery = stats.batteryPerHour(now, WakeWordSwitch.batteryPercent(context), charging)
    val wakes =
        pluralStringResource(
            R.plurals.wake_note_wakes,
            stats.wakes,
            stats.wakes,
            "%.1f".format(stats.hours(now)),
            "%.2f".format(perHour),
        )
    return if (battery == null) {
        wakes + "\n" + stringResource(R.string.wake_note_battery_unknown)
    } else {
        wakes + "\n" + stringResource(R.string.wake_note_battery, "%.1f".format(battery))
    }
}

private fun appInfo(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun kindOf(item: PhoneAccessItem): PhoneAccessKind = when (item) {
    PhoneAccessItem.CONTACTS -> PhoneAccessKind.Contacts
    PhoneAccessItem.PHONE -> PhoneAccessKind.Phone
    PhoneAccessItem.CALENDAR -> PhoneAccessKind.Calendar
    PhoneAccessItem.NOTIFICATIONS -> PhoneAccessKind.Notifications
    PhoneAccessItem.MICROPHONE -> PhoneAccessKind.Microphone
    PhoneAccessItem.ACCESSIBILITY -> PhoneAccessKind.ScreenControl
    PhoneAccessItem.BATTERY -> PhoneAccessKind.Background
    PhoneAccessItem.ASSISTANT -> PhoneAccessKind.Assistant
    PhoneAccessItem.WAKE_WORD -> PhoneAccessKind.WakeWord
    PhoneAccessItem.VOICE_CONFIRM -> PhoneAccessKind.VoiceConfirm
}

private fun itemOf(kind: PhoneAccessKind): PhoneAccessItem = PhoneAccessItem.entries.first { kindOf(it) == kind }

private fun titleOf(item: PhoneAccessItem): Int = when (item) {
    PhoneAccessItem.CONTACTS -> R.string.phone_access_item_contacts
    PhoneAccessItem.PHONE -> R.string.phone_access_item_phone
    PhoneAccessItem.CALENDAR -> R.string.phone_access_item_calendar
    PhoneAccessItem.NOTIFICATIONS -> R.string.phone_access_item_notifications
    PhoneAccessItem.MICROPHONE -> R.string.phone_access_item_microphone
    PhoneAccessItem.ACCESSIBILITY -> R.string.phone_access_item_screen
    PhoneAccessItem.BATTERY -> R.string.phone_access_item_background
    PhoneAccessItem.ASSISTANT -> R.string.phone_access_item_assistant
    PhoneAccessItem.WAKE_WORD -> R.string.phone_access_item_wake
    PhoneAccessItem.VOICE_CONFIRM -> R.string.phone_access_item_voice_confirm
}

private fun buttonOf(item: PhoneAccessItem, on: Boolean): Int = when {
    item == PhoneAccessItem.WAKE_WORD || item == PhoneAccessItem.VOICE_CONFIRM ->
        if (on) R.string.wake_turn_off else R.string.wake_turn_on
    on -> R.string.phone_access_button_review
    item == PhoneAccessItem.ACCESSIBILITY -> R.string.phone_access_accessibility_open
    item == PhoneAccessItem.BATTERY -> R.string.phone_access_battery_open
    item == PhoneAccessItem.ASSISTANT -> R.string.phone_access_assistant_open
    else -> R.string.phone_access_grant
}

/**
 * The card under a command that stopped for a missing grant. Grant asks Android; when Android
 * will no longer show its dialog, Grant opens App info → Permissions with a one-line instruction.
 * Coming back with the permission on resumes the action (task 23j).
 *
 * @param permission The Android permission that is off.
 * @param onOpenList Opens the Phone access screen.
 * @param onGranted Resumes the action that needed the permission.
 */
@Composable
fun MissingPermissionPrompt(
    permission: String,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier,
    onGranted: () -> Unit = {},
) {
    val context = LocalContext.current
    var deniedBefore by remember(permission) { mutableStateOf(false) }
    var sentToSettings by remember(permission) { mutableStateOf(false) }
    fun granted() = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (granted()) onGranted()
    }
    fun openAppSettings() {
        sentToSettings = true
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        settingsLauncher.launch(intent)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val activity = context.findActivity()
        val canShow = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        when (GrantRoute.next(granted = ok, canShowDialog = canShow, deniedBefore = true)) {
            GrantRoute.RESUME -> onGranted()
            GrantRoute.OPEN_APP_SETTINGS -> openAppSettings()
            GrantRoute.ASK -> deniedBefore = true
        }
    }
    // Back from Android settings with the permission on: resume without another tap.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(lifecycleState) {
        if (sentToSettings && lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && granted()) {
            sentToSettings = false
            onGranted()
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(all = 12.dp),
    ) {
        Text(stringResource(R.string.phone_access_card))
        if (sentToSettings || deniedBefore) Text(stringResource(R.string.phone_access_app_info_hint))
        TextButton(
            onClick = {
                val activity = context.findActivity()
                val canShow =
                    activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                when (GrantRoute.next(granted = granted(), canShowDialog = canShow, deniedBefore = deniedBefore)) {
                    GrantRoute.RESUME -> onGranted()
                    GrantRoute.OPEN_APP_SETTINGS -> openAppSettings()
                    GrantRoute.ASK -> launcher.launch(permission)
                }
            },
        ) {
            Text(stringResource(R.string.phone_access_grant))
        }
        TextButton(onClick = onOpenList) { Text(stringResource(R.string.phone_access_open)) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
    PhoneAccessItem.ASSISTANT -> R.string.phone_access_reason_assistant
    PhoneAccessItem.WAKE_WORD -> R.string.phone_access_reason_wake
    PhoneAccessItem.VOICE_CONFIRM -> R.string.phone_access_reason_voice_confirm
}

private fun openSafely(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}
