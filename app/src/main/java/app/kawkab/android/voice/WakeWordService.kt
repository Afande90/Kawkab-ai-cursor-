package app.kawkab.android.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.kawkab.android.R
import app.kawkab.android.crash.CrashLog
import app.kawkab.android.domain.constants.NotificationIds
import app.kawkab.android.domain.voice.WakeGate
import app.kawkab.android.domain.voice.WakeListeningMode
import app.kawkab.android.domain.voice.WakePauseReason
import app.kawkab.android.presentation.notifications.lockScreenVersion
import app.kawkab.android.presentation.ui.MainActivity
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Listens for "Kawkab" in a foreground service with a visible notification (task 27, spec §7).
 *
 * One background thread reads the microphone in 32 ms frames and gives them to [WakeSpotter].
 * On a wake it plays a short tone, lets go of the microphone and opens the Kawkab panel in
 * conversation mode; the panel calls [pause] and [resume] around the conversation. Audio is
 * never stored and never leaves the phone.
 *
 * Android allows a microphone service to start only while Kawkab is in front, so after a
 * restart it starts again the next time the owner opens the app ([startIfEnabled]).
 *
 * It runs in its own process (`:wake`, M1.8). onnxruntime's native start-up crashed with SIGSEGV
 * on the release build; a native crash cannot be caught, so the process boundary is what keeps
 * the app alive. The app reads Android's record of that crash and turns the wake word off with a
 * notice instead of starting it again. Talk with the app goes through [WakeRelayReceiver] and
 * package-only broadcasts, because the two processes share no memory.
 */
class WakeWordService : Service() {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private lateinit var capture: WakeMicSession
    private lateinit var usage: WakeMicUsage
    private lateinit var monitor: WakeListeningMonitor
    private var currentPause: WakePauseReason? = null

    // A binder only so the app process can watch this process die (M1.8); nothing is called on it.
    override fun onBind(intent: Intent?): IBinder = Binder()

    private val control = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PAUSE -> paused.set(true)
                ACTION_RESUME -> paused.set(false)
            }
            monitor.refresh()
        }
    }

    override fun onCreate() {
        super.onCreate()
        usage = WakeMicUsage(this)
        capture = WakeMicSession({ WakeAudioInput.open(this) ?: error("Microphone unavailable") }, usage::record)
        monitor = WakeListeningMonitor(this, paused::get) { reason ->
            currentPause = reason
            capture.setAllowed(reason == null)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                getSystemService(
                    NotificationManager::class.java,
                )?.notify(NotificationIds.WAKE_WORD_LISTENING, notification())
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_PAUSE)
            addAction(ACTION_RESUME)
        }
        ContextCompat.registerReceiver(this, control, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!micGranted(this) || WakeHealth.nativeCrashedSinceEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val config = WakeWordSwitch.readConfig(this, intent)
        try {
            startForeground(
                NotificationIds.WAKE_WORD_LISTENING,
                notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            if (config.mode == WakeListeningMode.OFF) {
                stopSelf()
                return START_NOT_STICKY
            }
            monitor.configure(config)
        } catch (error: Exception) {
            failed(error)
            return START_NOT_STICKY
        }
        if (running.compareAndSet(false, true)) {
            worker = Thread(::listen, "kawkab-wake").apply {
                priority = Thread.NORM_PRIORITY - 1
                start()
            }
        }
        // Not sticky: after a crash Android must not restart it in a loop; the app restarts it.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        capture.close()
        monitor.close()
        runCatching { unregisterReceiver(control) }
        worker?.join(JOIN_MS)
        worker = null
        super.onDestroy()
    }

    private fun listen() {
        val gate = WakeGate()
        var frames: WakeSpeechFrames? = null
        var previous: WakeCapture? = null
        var checkpoint = SystemClock.elapsedRealtime()
        try {
            while (running.get()) {
                if (previous != null && !capture.isCurrent(previous)) {
                    frames?.reset()
                    previous = null
                    usage.flush()
                }
                val elapsed = SystemClock.elapsedRealtime()
                if (elapsed - checkpoint >= USAGE_CHECKPOINT_MS) {
                    capture.checkpoint()
                    usage.flush()
                    checkpoint = elapsed
                }
                val mic = capture.acquireOrWait(USAGE_CHECKPOINT_MS) ?: continue
                val processor = frames ?: WakeSpeechFrames(assets, this).also { frames = it }
                if (previous !== mic) {
                    processor.reset()
                    previous = mic
                }
                if (!capture.isCurrent(mic)) continue
                val hit = processor.read(mic) { capture.isCurrent(mic) }
                if (hit && capture.isCurrent(mic) && gate.hit(SystemClock.elapsedRealtime(), paused.get())) {
                    capture.setAllowed(false)
                    woke()
                }
            }
        } catch (error: Exception) {
            if (running.get()) failed(error)
        } catch (error: LinkageError) {
            if (running.get()) failed(error)
        } finally {
            capture.close()
            try {
                frames?.close()
            } finally {
                usage.flush()
            }
        }
    }

    private fun failed(error: Throwable) {
        Timber.w("Wake listening stopped (%s)", error.javaClass.simpleName)
        WakeRelayReceiver.send(this, WakeRelayReceiver.ACTION_FAILED, error.javaClass.simpleName.ifBlank { "error" })
        running.set(false)
        stopSelf()
    }

    private fun woke() {
        paused.set(true)
        Handler(Looper.getMainLooper()).post { monitor.refresh() }
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME).apply {
                startTone(ToneGenerator.TONE_PROP_BEEP, TONE_MS)
                SystemClock.sleep(TONE_MS.toLong())
                release()
            }
        }
        // The app process opens the panel (or the tap-to-talk notification) and resumes listening.
        WakeRelayReceiver.send(this, WakeRelayReceiver.ACTION_WOKE)
    }

    private fun notification(): Notification {
        channel()
        val stop = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, WakeRelayReceiver::class.java).setAction(WakeRelayReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle(getString(R.string.wake_notification_title))
            .setContentText(currentPause?.let { "Paused: ${it.label}" } ?: getString(R.string.wake_notification_body))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp())
            .addAction(0, getString(R.string.wake_notification_stop), stop)
            .lockScreenVersion(this, CHANNEL, R.drawable.ic_stat_agent, getString(R.string.wake_notification_title))
            .build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun channel() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.wake_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    /** Starting, stopping and pausing from the app and the panel. */
    companion object {

        /** Panel argument: opened by the wake word, so conversation mode starts at once. */
        const val EXTRA_WAKE: String = "app.kawkab.android.WAKE"

        private const val ACTION_PAUSE = "app.kawkab.android.WAKE_PAUSE"
        private const val ACTION_RESUME = "app.kawkab.android.WAKE_RESUME"
        private const val USAGE_CHECKPOINT_MS = 60_000L
        private const val JOIN_MS = 1_000L
        private const val TONE_VOLUME = 80
        private const val TONE_MS = 150

        private val paused = AtomicBoolean(false)

        /** The channel of the listening notification; the relay posts its notice on it too. */
        const val CHANNEL: String = "wake_word"

        /** The panel holds the microphone: stop listening for the wake word (any process). */
        fun pause(context: Context) = control(context, ACTION_PAUSE)

        /** The panel closed: listen for the wake word again (any process). */
        fun resume(context: Context) = control(context, ACTION_RESUME)

        private fun control(context: Context, action: String) {
            context.sendBroadcast(Intent(action).setPackage(context.packageName))
        }

        /**
         * Starts listening. Does nothing without the microphone permission or when Android
         * refuses (the app is not in front).
         *
         * @param context Any context.
         */
        fun start(context: Context) {
            if (!micGranted(context)) return
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    WakeWordSwitch.attachConfig(context, Intent(context, WakeWordService::class.java)),
                )
            }
                .onSuccess { watch(context.applicationContext) }
                .onFailure { WakeWordSwitch.failed(context, it.javaClass.simpleName.ifBlank { "error" }) }
        }

        @Volatile
        private var watching: ServiceConnection? = null

        /**
         * Binds without creating, so the app hears at once when the wake process dies while the
         * wake word is on, and turns it off with the reason (M1.8).
         */
        private fun watch(app: Context) {
            if (watching != null) return
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) = Unit
                override fun onServiceDisconnected(name: ComponentName?) {
                    if (WakeWordSwitch.isEnabled(app)) WakeWordSwitch.failed(app, WakeHealth.NATIVE_CRASH)
                }
                override fun onBindingDied(name: ComponentName?) {
                    app.unbindService(this)
                    watching = null
                }
            }
            if (app.bindService(Intent(app, WakeWordService::class.java), connection, 0)) watching = connection
        }

        /** @param context Any context. */
        fun stop(context: Context) {
            watching?.let { connection ->
                runCatching { context.applicationContext.unbindService(connection) }
                watching = null
            }
            context.stopService(Intent(context, WakeWordService::class.java))
        }

        /**
         * Starts listening when the owner turned the wake word on. Called when Kawkab comes to
         * the front, the only time Android lets a microphone service start.
         *
         * @param context Any context.
         */
        fun startIfEnabled(context: Context) {
            // A safe-mode start leaves the wake word off for this run (task 32).
            if (CrashLog.safeMode || !WakeWordSwitch.isEnabled(context)) return
            // Its process died in native code since it was turned on: off, with the reason (M1.8).
            if (WakeHealth.nativeCrashedSinceEnabled(context)) {
                WakeWordSwitch.failed(context, WakeHealth.NATIVE_CRASH)
                return
            }
            start(context)
        }

        private fun micGranted(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }
}
