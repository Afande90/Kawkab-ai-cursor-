package app.kawkab.android.presentation.ui.pocket

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kawkab.android.BuildConfig
import app.kawkab.android.assistant.AssistantRoleRequest
import app.kawkab.android.crash.CrashLog
import app.kawkab.android.domain.models.ChatMessage
import app.kawkab.android.domain.phoneaccess.PhoneAccessItem
import app.kawkab.android.domain.phoneaccess.PhoneAccessStatus
import app.kawkab.android.domain.text.ReplyLanguage
import app.kawkab.android.domain.voice.ConversationLoop
import app.kawkab.android.domain.voice.SpokenOwnerAnswer
import app.kawkab.android.glyph.GlyphMirror
import app.kawkab.android.hands.ContactChoice
import app.kawkab.android.presentation.ui.chat.home.ActionSentence
import app.kawkab.android.presentation.ui.chat.home.ChatHomeViewModel
import app.kawkab.android.presentation.ui.common.resolve
import app.kawkab.android.presentation.ui.growth.GrowthViewModel
import app.kawkab.android.presentation.ui.models.ModelsViewModel
import app.kawkab.android.presentation.ui.phoneaccess.AndroidPhoneAccess
import app.kawkab.android.voice.ConversationDriver
import app.kawkab.android.voice.OfflineSpeech
import app.kawkab.android.voice.OfflineVoice
import app.kawkab.android.voice.OwnerAudioPipe
import app.kawkab.android.voice.OwnerVoice
import app.kawkab.android.voice.WakeWordSwitch
import app.kawkab.design.components.chat.ChatContent
import app.kawkab.design.components.chat.ChatRole
import app.kawkab.design.device.DeviceKey
import app.kawkab.design.device.DeviceLearning
import app.kawkab.design.device.DevicePage
import app.kawkab.design.device.DeviceShot
import app.kawkab.design.device.DeviceSignal
import app.kawkab.design.device.DeviceState
import app.kawkab.design.device.KawkabDevice
import app.kawkab.design.device.LcdLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/** The owner's product surface. The engine is reachable only through Settings → Advanced. */
@Composable
fun PocketHomeScreen(
    viewModel: ChatHomeViewModel,
    onAdvanced: () -> Unit,
    initialMenu: String? = null,
    initialPage: DevicePage? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps =
        remember { dagger.hilt.android.EntryPointAccessors.fromApplication(context, PocketServices::class.java).apps() }
    val pendingApp by apps.state.collectAsState()
    val appNames by apps.learned.collectAsState()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingContact by viewModel.pendingContact.collectAsState()
    val growth: GrowthViewModel = hiltViewModel()
    val skills by growth.skills.collectAsState()
    val aliases by growth.aliases.collectAsState()
    val memories by growth.memories.collectAsState()
    val past by growth.past.collectAsState()
    val models: ModelsViewModel = hiltViewModel()
    val modelState by models.uiState.collectAsState()
    val owner = remember { OwnerVoice(context) }
    val filing = remember { ShotCategories(context) }
    var shots by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var categories by remember { mutableStateOf(filing.all()) }
    val reviewShot = shots.firstOrNull { it.attachment?.path !in categories }

    var enrolled by remember { mutableStateOf(owner.enrolled()) }
    var page by remember { mutableStateOf(if (enrolled) initialPage ?: DevicePage.IDLE else DevicePage.ENROLL) }
    var menu by remember { mutableStateOf(initialMenu) }
    var phase by remember { mutableStateOf(ConversationLoop.Phase.IDLE) }
    var heard by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var enrollmentCount by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var decisionQuestion by remember { mutableStateOf<Pair<String, String>?>(null) }
    var conversationQuestion by remember { mutableStateOf("") }
    var forget by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val voice = remember { OfflineVoice(context) }
    var driver by remember { mutableStateOf<ConversationDriver?>(null) }
    val lastReply = state.messages.lastOrNull { it.role == ChatRole.Assistant }?.let {
        when (val text = it.content) {
            is ChatContent.Text -> text.text
            is ChatContent.Markdown -> text.source
            else -> ""
        }
    }.orEmpty()
    val failure = (state.visual as? app.kawkab.android.presentation.ui.chat.home.ChatHomeUiState.Error)
        ?.message?.let(PocketErrors::reply)
    LaunchedEffect(failure) { if (failure != null) driver?.replied(failure) }
    val pending = state.pending.tool
    fun send(text: String) {
        editing = false
        val forgetting = forget
        if (forgetting != null) {
            if (SpokenOwnerAnswer.consumeYes()) {
                if (forgetting == "voice") {
                    owner.forget()
                    enrolled = false
                    page = DevicePage.ENROLL
                } else if (forgetting.startsWith("app:")) {
                    apps.forget(forgetting.removePrefix("app:"))
                } else if (forgetting.startsWith("shot:")) {
                    filing.forget(forgetting.removePrefix("shot:"))
                    categories = filing.all()
                } else if (forgetting.startsWith("past:")) {
                    growth.forgetPast(forgetting.removePrefix("past:").toLong())
                } else if (forgetting.startsWith("memory:")) {
                    growth.forgetMemory(forgetting.removePrefix("memory:").toLong())
                } else if (forgetting.startsWith("alias:")) {
                    growth.forgetContact(forgetting.removePrefix("alias:"))
                } else {
                    growth.delete(forgetting.removePrefix("skill:"))
                }
                notice = "Forgotten."
            } else {
                notice = "Kept it."
            }
            driver?.replied(notice)
            forget = null
            return
        }
        if (page == DevicePage.SHOTS && reviewShot != null) {
            val category = filing.answer(text)
            if (category == null) {
                conversationQuestion = "Is it a job, bill or place?"
            } else {
                filing.remember(reviewShot.attachment!!.path, category)
                categories = filing.all()
                notice = "Filed as $category."
                driver?.replied(notice)
            }
            return
        }
        if (state.visual is app.kawkab.android.presentation.ui.chat.home.ChatHomeUiState.Generating) return
        val previous = decisionQuestion
        var forced: Boolean? = null
        val input = if (previous != null) {
            decisionQuestion = null
            if (previous.second == app.kawkab.android.domain.decision.DecisionCoordinator.INTENT_QUESTION) {
                forced = when (text.trim().lowercase()) {
                    "command", "action", "do it", "1", "أمر", "نفذ" -> true
                    "chat", "question", "2", "سؤال", "كلام" -> false
                    else -> {
                        decisionQuestion = previous
                        conversationQuestion = "Say command or question."
                        return
                    }
                }
                previous.first
            } else {
                "${previous.first} $text"
            }
        } else {
            text
        }
        notice = ""
        heard = input
        menu = null
        page = DevicePage.TALK
        scope.launch {
            val services = dagger.hilt.android.EntryPointAccessors.fromApplication(context, PocketServices::class.java)
            services.judge().begin(viewModel.state.value.thread.currentSessionId, input)
            val known = app.kawkab.android.domain.fastpath.CommandFastPath.parse(input) != null ||
                pendingContact != null ||
                pendingApp != null
            val choice = if (forced == null &&
                !known
            ) {
                services.decisions().route(input, viewModel.state.value.thread.currentSessionId)
            } else {
                null
            }
            if (choice is app.kawkab.android.domain.decision.DecisionResult.Ask) {
                decisionQuestion = input to choice.question
                notice = choice.question
                conversationQuestion = choice.question
                return@launch
            }
            val mayAct =
                forced ?: ((choice as? app.kawkab.android.domain.decision.DecisionResult.Chosen)?.option != "chat")
            services.intents().remember(viewModel.state.value.thread.currentSessionId, mayAct)
            val request = if (mayAct) input else "Answer conversationally without phone actions: $input"
            viewModel.onComposerValueChange(request)
            viewModel.sendMessage()
        }
    }
    fun conversation(): ConversationDriver = driver ?: ConversationDriver(
        OfflineSpeech(context),
        voice,
        ::send,
        { phase = it },
        answer = { text ->
            val currentPending = viewModel.state.value.pending.tool
            if (currentPending != null) {
                if (app.kawkab.android.domain.voice.VoiceAnswers.answer(text) ==
                    app.kawkab.android.domain.voice.VoiceAnswers.Answer.NO
                ) {
                    viewModel.hitl.rejectTool()
                } else if (app.kawkab.android.domain.voice.VoiceAnswers.answer(text) ==
                    app.kawkab.android.domain.voice.VoiceAnswers.Answer.YES
                ) {
                    viewModel.hitl.approveTool()
                }
            } else {
                send(text)
            }
        },
    ).also { driver = it }
    LaunchedEffect(state.thread.currentSessionId) {
        if (state.thread.currentSessionId.isNotBlank()) {
            dagger.hilt.android.EntryPointAccessors.fromApplication(context, PocketServices::class.java)
                .entries().voiceRequests.collect { conversation().handoff(it) }
        }
    }
    LaunchedEffect(conversationQuestion) {
        if (conversationQuestion.isNotBlank()) {
            conversation().ask(conversationQuestion)
            conversationQuestion = ""
        }
    }
    LaunchedEffect(page, categories) {
        if (page == DevicePage.SHOTS) {
            val chats = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context,
                PocketServices::class.java,
            ).chats()
            shots = withContext(Dispatchers.IO) {
                chats.getAllSessions().flatMap { chats.getMessagesForSession(it).first() }
                    .filter { it.attachment != null }.sortedByDescending { it.timestamp }.take(SHOTS_LIMIT)
            }
            if (shots.any {
                    it.attachment?.path !in categories
                }
            ) {
                conversation().ask("Is this screenshot a job, bill or place?")
            }
        }
    }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick += 1 }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick += 1 }
    suspend fun recordEnrollment() {
        recording = true
        app.kawkab.android.voice.WakeWordService.pause(context)
        enrollmentCount = 0
        val clips = mutableListOf<FloatArray>()
        try {
            repeat(ENROLLMENT_CLIPS) { index ->
                val prompt = when {
                    index < WAKE_CLIPS -> "Say Kawkab"
                    index == WAKE_CLIPS -> "Say: this is my phone"
                    else -> "Say: I speak and you listen"
                }
                notice = prompt
                suspendCancellableCoroutine { continuation ->
                    voice.speak(prompt, ReplyLanguage.ENGLISH) { if (continuation.isActive) continuation.resume(Unit) }
                }
                val clip = withContext(Dispatchers.IO) {
                    val pipe = OwnerAudioPipe(context)
                    try {
                        pipe.start()
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe.source).use {
                            it.readNBytes(ENROLLMENT_BYTES)
                        }
                        pipe.samples()
                    } finally {
                        pipe.close()
                    }
                }
                withContext(Dispatchers.Default) { owner.embedding(clip) }
                clips += clip
                enrollmentCount = index + 1
            }
            withContext(Dispatchers.Default) { owner.enroll(clips) }
            enrolled = true
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context,
                PocketServices::class.java,
            ).appState().setHasCompletedOnboarding(true)
            WakeWordSwitch.set(context, true)
            page = DevicePage.IDLE
            notice = "Your voice is ready. Only you can wake me."
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            notice = "I could not enroll that voice. Use a quiet room and try again."
        } finally {
            recording = false
            app.kawkab.android.voice.WakeWordService.resume(context)
        }
    }
    val credential = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            scope.launch { recordEnrollment() }
        } else {
            notice = "Enrollment needs the phone owner's screen lock."
        }
    }
    fun enroll() {
        if (recording) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            microphone.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val guard = context.getSystemService(KeyguardManager::class.java)

        @Suppress("DEPRECATION")
        val intent = guard?.createConfirmDeviceCredentialIntent(
            "My voice",
            "Confirm that you own this phone before enrolling.",
        )
        if (intent != null) credential.launch(intent) else notice = "Set a screen lock before enrolling your voice."
    }
    DisposableEffect(Unit) {
        onDispose {
            driver?.release()
            voice.shutdown()
        }
    }
    LifecycleResumeEffect(state.thread.currentSessionId) {
        viewModel.onChatScreenVisible()
        onPauseOrDispose {
            viewModel.onChatScreenHidden()
            if (phase != ConversationLoop.Phase.IDLE) driver?.tap()
        }
    }
    LaunchedEffect(pending?.requestId) {
        if (pending != null) {
            val sentence = context.resolve(
                ActionSentence.announcement(pending.toolName, ActionSentence.arguments(pending.arguments)),
            )
            if (pending.runsAtMs !=
                null
            ) {
                conversation().announce(sentence) {
                    dagger.hilt.android.EntryPointAccessors.fromApplication(context, PocketServices::class.java)
                        .gate().announcementFinished(state.thread.currentSessionId, pending.requestId)
                }
            } else {
                conversation().ask("$sentence. Say yes or cancel.")
            }
        }
    }
    LaunchedEffect(pendingApp) {
        pendingApp?.let { conversation().ask(app.kawkab.android.hands.AppChoiceRules.question(it)) }
    }
    LaunchedEffect(pendingContact) {
        pendingContact?.let {
            conversation().ask(
                ContactChoice.question(
                    it.args[ContactChoice.SPOKEN].orEmpty(),
                    it.choices.size,
                    it.choices.map { c ->
                        c.name
                    },
                ),
            )
        }
    }
    LaunchedEffect(lastReply) {
        if (pendingContact == null &&
            pendingApp == null &&
            pending == null
        ) {
            driver?.replied(lastReply)
        }
    }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pending?.requestId) {
        while (pending?.runsAtMs !=
            null
        ) {
            now = System.currentTimeMillis()
            delay(COUNTDOWN_TICK_MS)
        }
    }
    BackHandler(menu != null || page != DevicePage.IDLE) {
        menu = null
        page =
            if (enrolled) DevicePage.IDLE else DevicePage.ENROLL
    }
    val shownPage = when {
        menu != null -> DevicePage.TALK
        pending?.runsAtMs != null -> DevicePage.CALLING
        pendingContact != null || pendingApp != null -> DevicePage.WHICH
        phase == ConversationLoop.Phase.LISTENING -> DevicePage.LISTENING
        else -> page
    }
    val signal = when {
        pending?.runsAtMs != null -> DeviceSignal.ACTING
        pendingContact != null || pendingApp != null || pending != null -> DeviceSignal.ASKING
        phase == ConversationLoop.Phase.LISTENING -> DeviceSignal.LISTENING
        phase == ConversationLoop.Phase.SPEAKING -> DeviceSignal.SPEAKING
        recording -> DeviceSignal.LISTENING
        else -> DeviceSignal.STANDBY
    }
    val args = pending?.let { ActionSentence.arguments(it.arguments) }.orEmpty()
    LaunchedEffect(page) { if (page == DevicePage.GROWTH) growth.refresh() }
    val learned =
        memories.map { DeviceLearning("memory:${it.id}", it.text) } +
            aliases.map { DeviceLearning("alias:${it.key}", "${it.key} means ${it.value.name}") } +
            skills.map { DeviceLearning("skill:${it.key}", "${it.key} — ${it.successes} successes") }
    val device = DeviceState(
        page = shownPage, signal = signal,
        date = SimpleDateFormat("EEE dd MMM", Locale.ENGLISH).format(Date()),
        battery = "${context.getSystemService(
            BatteryManager::class.java,
        )?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0}%",
        heard = heard, reply = failure ?: notice.ifBlank { lastReply },
        result = lastReply.takeIf {
            listOf("Alarm", "Timer", "Called", "SMS submitted", "Sent", "Opened", "Reminder").any(it::startsWith)
        }.orEmpty(),
        name =
        args["name"] ?: args["spoken"] ?: args["contact"]
            ?: pendingContact?.args?.get(ContactChoice.SPOKEN) ?: pendingApp?.spoken.orEmpty(),
        actionLabel = if (pending?.toolName == "call_phone") "CALLING" else "ACTING",
        number = args["number"] ?: args["contact"].orEmpty(), usual = args["spoken"]?.let { it in aliases } == true,
        choices = pendingContact?.choices?.map { it.name } ?: pendingApp?.options?.map { it.label }.orEmpty(),
        seconds = (((pending?.runsAtMs ?: now) - now + SECOND_MS - 1) / SECOND_MS).toInt().coerceIn(
            0,
            COUNTDOWN_SECONDS,
        ),
        countdownProgress = pending?.runsAtMs?.let {
            (1f - (it - now).toFloat() / (COUNTDOWN_SECONDS * SECOND_MS)).coerceIn(0f, 1f)
        },
        enrolled = enrollmentCount, learned = learned,
        shots = shots.mapIndexed { index, shot ->
            DeviceShot("SCREENSHOT ${index + 1}", categories[shot.attachment?.path] ?: "UNFILED", shot == reviewShot)
        },
    )
    GlyphMirror(device)
    KawkabDevice(
        device,
        onHome = {
            if (pending?.runsAtMs != null) viewModel.hitl.rejectTool()
            menu = null
            page = if (enrolled) DevicePage.IDLE else DevicePage.ENROLL
        },
        onShots = {
            if (pending?.runsAtMs != null) viewModel.hitl.rejectTool()
            menu = null
            page = DevicePage.SHOTS
        },
        onGrowth = {
            if (pending?.runsAtMs != null) viewModel.hitl.rejectTool()
            menu = null
            page = DevicePage.GROWTH
        },
        onSettings = {
            if (pending?.runsAtMs != null) viewModel.hitl.rejectTool()
            menu = "SETTINGS"
        },
        onTalk = {
            if (pending?.runsAtMs != null) {
                viewModel.hitl.rejectTool()
            } else if (!enrolled) {
                menu = null
                page = DevicePage.ENROLL
                enroll()
            } else {
                conversation().tap()
            }
        },
        onVoice = {
            menu = null
            page = DevicePage.ENROLL
            if (!enrolled) enroll()
        },
        onForget = { id ->
            SpokenOwnerAnswer.clear()
            forget = id
            conversation().ask("Forget this learned item? Say yes or cancel.")
        },
        onLcdTap = { if (shownPage in listOf(DevicePage.IDLE, DevicePage.TALK)) editing = true },
        lcdFooter = if (editing && menu == null && shownPage in listOf(DevicePage.IDLE, DevicePage.TALK)) {
            {
                BasicTextField(draft, { draft = it }, Modifier.fillMaxWidth().heightIn(min = 48.dp))
                if (draft.isNotBlank()) {
                    DeviceKey("SEND", onClick = {
                        val text = draft
                        draft = ""
                        send(text)
                    })
                }
            }
        } else {
            null
        },
        lcdContent = if (menu == null) {
            null
        } else {
            {
                LcdLine(menu.orEmpty())
                when (menu) {
                    "SETTINGS" -> PocketSettings.entries.forEach { entry ->
                        DeviceKey(entry, onClick = {
                            if (entry == "ADVANCED") onAdvanced() else menu = entry
                        })
                    }
                    "VOICE" -> {
                        LcdLine(if (enrolled) "ONLY YOUR VOICE IS ACCEPTED" else "ENROLL YOUR VOICE FIRST")
                        DeviceKey("MY VOICE", onClick = {
                            menu = null
                            page = DevicePage.ENROLL
                            if (enrolled) {
                                SpokenOwnerAnswer.clear()
                                forget = "voice"
                                conversation().ask(
                                    "Delete your current voice profile before enrolling again? Say yes or cancel.",
                                )
                            } else {
                                enroll()
                            }
                        })
                        DeviceKey(
                            if (WakeWordSwitch.isEnabled(
                                    context,
                                )
                            ) {
                                "WAKE WORD: ON"
                            } else {
                                "WAKE WORD: OFF"
                            },
                            onClick = {
                                if (enrolled) WakeWordSwitch.set(context, !WakeWordSwitch.isEnabled(context))
                                tick += 1
                            },
                        )
                        DeviceKey("DELETE MY RECORDINGS", onClick = {
                            SpokenOwnerAnswer.clear()
                            forget = "voice"
                            conversation().ask("Delete your voice recordings? Say yes or cancel.")
                        })
                    }
                    "PHONE ACCESS" -> {
                        val smsOn = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                            PackageManager.PERMISSION_GRANTED
                        DeviceKey("TEXT MESSAGES: ${if (smsOn) "ON" else "OFF"}", onClick = {
                            microphone.launch(Manifest.permission.SEND_SMS)
                        })
                        AndroidPhoneAccess.lines(context).filter {
                            it.item !=
                                PhoneAccessItem.VOICE_CONFIRM
                        }.forEach { line ->
                            DeviceKey(
                                "${line.item.name.replace(
                                    '_',
                                    ' ',
                                )}: ${if (line.on) "ON" else "OFF"}",
                                onClick = {
                                    val permission = PhoneAccessStatus.permission(line.item)
                                    if (permission != null) {
                                        microphone.launch(permission)
                                    } else {
                                        settings.launch(
                                            when (line.item) {
                                                PhoneAccessItem.ACCESSIBILITY ->
                                                    AndroidPhoneAccess.accessibilitySettings()
                                                PhoneAccessItem.BATTERY -> AndroidPhoneAccess.batterySettings(context)
                                                PhoneAccessItem.ASSISTANT -> AssistantRoleRequest.create(context)
                                                    ?: AssistantRoleRequest.powerButtonSettings(context)
                                                else -> Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    "package:${context.packageName}".toUri(),
                                                )
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                    "MODELS" -> {
                        LcdLine(
                            if (modelState.isDownloading) {
                                "DOWNLOADING ${modelState.downloadProgress ?: 0}%"
                            } else {
                                "ALL MODELS RUN ON THIS PHONE"
                            },
                        )
                        modelState.downloadedModels.filterNot {
                            it.path.endsWith(app.kawkab.android.data.decision.AndroidTypedJudge.FILE_NAME)
                        }.forEach { model ->
                            DeviceKey(
                                if (model.isActive) "CURRENT LANGUAGE MODEL" else "USE LANGUAGE MODEL",
                                onClick = {
                                    models.setActiveModel(model.id)
                                },
                            )
                        }
                        DeviceKey("GET DECISION MODEL", onClick = {
                            models.startDownload(
                                app.kawkab.android.data.decision.AndroidTypedJudge.URL,
                                app.kawkab.android.data.decision.AndroidTypedJudge.FILE_NAME,
                            )
                        })
                        DeviceKey("GET LANGUAGE & IMAGES", onClick = {
                            models.startDownload(GEMMA_URL, "gemma-4-E2B-it.litertlm")
                        })
                    }
                    "PRIVACY" -> {
                        LcdLine("EVERYTHING STAYS ON THIS PHONE. NO TELEMETRY. NO CLOUD VOICES.")
                        LcdLine("BANKING, PAYMENT AND PASSWORD SCREENS ARE BLOCKED.")
                    }
                    "ABOUT" -> {
                        LcdLine("KAWKAB ${BuildConfig.VERSION_NAME}")
                        LcdLine(
                            "BUILT ON KNOTWORK. FONTS: VT323, IBM PLEX MONO, NOTO KUFI ARABIC. SPEECH: SHERPA-ONNX.",
                        )
                        DeviceKey("COPY CRASH REPORT", onClick = {
                            context.getSystemService(
                                ClipboardManager::class.java,
                            ).setPrimaryClip(ClipData.newPlainText("Kawkab crash report", CrashLog.report(context)))
                        })
                    }
                }
                DeviceKey("BACK", onClick = { menu = if (menu == "SETTINGS") null else "SETTINGS" })
            }
        },
    )
}

/** The complete ordinary settings menu; engine surfaces are nested under Advanced. */
object PocketSettings {
    /** English-only owner controls, in the required order. */
    val entries = listOf("VOICE", "PHONE ACCESS", "MODELS", "PRIVACY", "ABOUT", "ADVANCED")
}

private const val ENROLLMENT_CLIPS = 12
private const val WAKE_CLIPS = 10
private const val ENROLLMENT_BYTES = 96_000
private const val COUNTDOWN_TICK_MS = 100L
private const val SECOND_MS = 1_000L
private const val GEMMA_URL =
    "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

private const val COUNTDOWN_SECONDS = 3

private const val SHOTS_LIMIT = 20
