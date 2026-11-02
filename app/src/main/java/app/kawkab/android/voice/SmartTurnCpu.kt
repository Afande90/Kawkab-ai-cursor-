package app.kawkab.android.voice

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import app.kawkab.android.domain.voice.SmartTurnFeatures
import app.kawkab.android.domain.voice.TurnCompletionModel
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Cached CPU-only smart-turn-v3.2 session; the asset is downloaded and verified by Gradle. */
object SmartTurnCpu {
    private const val MODEL = "voice/smart-turn-v3.2-cpu.onnx"
    private const val SHA256 = "2bb026316b14a660486a75b1733cd3fbab8c2fd0314dc9af7be49f8cca967e4f"
    private val loader = Executors.newSingleThreadExecutor { job -> Thread(job, "turn-load").apply { isDaemon = true } }
    private var loading: Future<OrtSession>? = null

    /** Each listening session owns its cancellation token; cancelling an old one cannot terminate a new turn. */
    @Synchronized
    fun forSession(context: Context): TurnCompletionModel {
        val assets = context.applicationContext.assets
        val future = loading ?: loader.submit<OrtSession> {
            val bytes = assets.open(MODEL).use { it.readBytes() }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            check(hash == SHA256) { "Turn model checksum mismatch" }
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(1)
                options.setInterOpNumThreads(1)
                options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                options.addCPU(true)
                OrtEnvironment.getEnvironment().createSession(bytes, options)
            }
        }.also { loading = it }
        return CpuTurn(future)
    }

    private class CpuTurn(private val future: Future<OrtSession>) : TurnCompletionModel {
        private val guard = Any()
        private var running: OrtSession.RunOptions? = null

        @Volatile private var cancelled = false

        override fun probability(samples: FloatArray): Float {
            check(!cancelled)
            val session = future.get()
            val features = SmartTurnFeatures.extract(samples)
            return synchronized(session) {
                check(!cancelled && !Thread.currentThread().isInterrupted)
                OrtSession.RunOptions().use { options ->
                    synchronized(guard) {
                        check(!cancelled)
                        running = options
                    }
                    try {
                        OnnxTensor.createTensor(
                            OrtEnvironment.getEnvironment(),
                            FloatBuffer.wrap(features),
                            longArrayOf(1, SmartTurnFeatures.BANDS.toLong(), SmartTurnFeatures.FRAMES.toLong()),
                        ).use { input ->
                            session.run(mapOf("input_features" to input), options).use { result ->
                                (result[0] as OnnxTensor).floatBuffer.get(0)
                            }
                        }
                    } finally {
                        synchronized(guard) { running = null }
                    }
                }
            }
        }

        override fun cancel() {
            synchronized(guard) {
                cancelled = true
                runCatching { running?.setTerminate(true) }
            }
        }
    }
}
