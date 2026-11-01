package app.kawkab.android.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.kawkab.android.R
import app.kawkab.android.assistant.KawkabVoiceInteractionService
import app.kawkab.android.domain.voice.WakeGate
import app.kawkab.android.domain.voice.WakeWord
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
 */
class WakeWordService : Service() {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            WakeWordSwitch.set(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!micGranted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (running.compareAndSet(false, true)) {
            worker = Thread(::listen, "kawkab-wake").apply {
                priority = Thread.NORM_PRIORITY - 1
                start()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        worker?.join(JOIN_MS)
        worker = null
        super.onDestroy()
    }

    private fun listen() {
        val gate = WakeGate()
        val spotter = runCatching { WakeSpotter(assets) }.getOrElse {
            Timber.w(it, "Wake models did not load")
            running.set(false)
            stopSelf()
            return
        }
        spotter.use {
            var record: AudioRecord? = null
            val pcm = ShortArray(WakeWord.FRAME_SAMPLES)
            val frame = FloatArray(WakeWord.FRAME_SAMPLES)
            while (running.get()) {
                if (paused.get()) {
                    record?.release()
                    record = null
                    SystemClock.sleep(PAUSE_POLL_MS)
                    continue
                }
                val mic = record ?: openMic()?.also {
                    record = it
                    spotter.reset()
                } ?: run {
                    SystemClock.sleep(PAUSE_POLL_MS)
                    null
                } ?: continue
                if (mic.read(pcm, 0, pcm.size) != pcm.size) continue
                for (i in pcm.indices) frame[i] = pcm[i] / PCM_SCALE
                if (spotter.feed(frame) && gate.hit(SystemClock.elapsedRealtime(), paused.get())) {
                    mic.release()
                    record = null
                    woke()
                }
            }
            record?.release()
        }
    }

    private fun openMic(): AudioRecord? = runCatching {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val min = AudioRecord.getMinBufferSize(
            WakeWord.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            WakeWord.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(min, WakeWord.FRAME_SAMPLES * BUFFER_FRAMES * 2),
        ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }?.apply { startRecording() }
    }.getOrNull()

    private fun woke() {
        WakeWordSwitch.countWake(this)
        if (WakeWordSwitch.isCharging(this)) WakeWordSwitch.noteCharging(this)
        paused.set(true)
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME).apply {
                startTone(ToneGenerator.TONE_PROP_BEEP, TONE_MS)
                SystemClock.sleep(TONE_MS.toLong())
                release()
            }
        }
        val args = Bundle().apply { putBoolean(EXTRA_WAKE, true) }
        if (!KawkabVoiceInteractionService.showPanel(args)) {
            // Not the assistant: Android does not let a background service open a screen, so a
            // notification asks for one tap instead.
            notify(tapToTalk())
            SystemClock.sleep(NO_PANEL_RESUME_MS)
            paused.set(false)
        }
    }

    private fun notification(): Notification {
        channel()
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle(getString(R.string.wake_notification_title))
            .setContentText(getString(R.string.wake_notification_body))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp())
            .addAction(0, getString(R.string.wake_notification_stop), stop)
            .build()
    }

    private fun tapToTalk(): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_agent)
        .setContentTitle(getString(R.string.wake_heard_title))
        .setContentText(getString(R.string.wake_heard_body))
        .setAutoCancel(true)
        .setTimeoutAfter(HEARD_TIMEOUT_MS)
        .setContentIntent(openApp())
        .build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notify(notification: Notification) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            getSystemService(NotificationManager::class.java)?.notify(HEARD_ID, notification)
        }
    }

    private fun channel() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.wake_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
    }

    /** Starting, stopping and pausing from the app and the panel. */
    companion object {
        /** Panel argument: opened by the wake word, so conversation mode starts at once. */
        const val EXTRA_WAKE: String = "app.kawkab.android.WAKE"

        private const val ACTION_STOP = "app.kawkab.android.WAKE_STOP"
        private const val CHANNEL = "wake_word"
        private const val NOTIFICATION_ID = 4_270
        private const val HEARD_ID = 4_271
        private const val PCM_SCALE = 32_768f
        private const val BUFFER_FRAMES = 8
        private const val PAUSE_POLL_MS = 200L
        private const val JOIN_MS = 1_000L
        private const val TONE_VOLUME = 80
        private const val TONE_MS = 150
        private const val NO_PANEL_RESUME_MS = 3_000L
        private const val HEARD_TIMEOUT_MS = 30_000L

        private val paused = AtomicBoolean(false)

        /** The panel holds the microphone: stop listening for the wake word. */
        fun pause() = paused.set(true)

        /** The panel closed: listen for the wake word again. */
        fun resume() = paused.set(false)

        /**
         * Starts listening. Does nothing without the microphone permission or when Android
         * refuses (the app is not in front).
         *
         * @param context Any context.
         */
        fun start(context: Context) {
            if (!micGranted(context)) return
            paused.set(false)
            runCatching { ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java)) }
        }

        /** @param context Any context. */
        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }

        /**
         * Starts listening when the owner turned the wake word on. Called when Kawkab comes to
         * the front, the only time Android lets a microphone service start.
         *
         * @param context Any context.
         */
        fun startIfEnabled(context: Context) {
            if (WakeWordSwitch.isEnabled(context)) start(context)
        }

        private fun micGranted(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }
}
