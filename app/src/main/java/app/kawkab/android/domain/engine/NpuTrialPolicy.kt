package app.kawkab.android.domain.engine

import app.kawkab.android.domain.models.LocalBackend

/**
 * Decides whether a measured NPU self-test may replace CPU.
 *
 * NPU stays off unless the probe started and decoded faster than CPU. A crash,
 * a missing number, or a slower run keeps CPU. Nothing in this build calls the
 * policy with a phone measurement, so the engine does not offer NPU.
 */
object NpuTrialPolicy {
    /**
     * One backend's self-test.
     *
     * @property started `true` when the backend initialised and produced tokens.
     * @property tokensPerSecond Decode speed from that run. Ignored when [started] is false.
     */
    data class Probe(val started: Boolean, val tokensPerSecond: Float)

    /**
     * Picks the backend the next launch may use.
     *
     * @param cpu The CPU probe.
     * @param npu The NPU probe.
     * @return [LocalBackend.NPU] only when [npu] started and was faster than [cpu].
     */
    fun choose(cpu: Probe, npu: Probe): LocalBackend {
        if (!npu.started) return LocalBackend.CPU
        if (npu.tokensPerSecond > cpu.tokensPerSecond) return LocalBackend.NPU
        return LocalBackend.CPU
    }
}
