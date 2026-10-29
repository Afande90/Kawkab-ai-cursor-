package app.kawkab.android.domain.engine

import app.kawkab.android.domain.models.LocalBackend
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the rule that keeps NPU off until a phone self-test beats CPU.
 */
class NpuTrialPolicyTest {

    private val cpu = NpuTrialPolicy.Probe(started = true, tokensPerSecond = 10f)

    @Test
    fun `given an NPU probe that did not start when choosing then CPU stays`() {
        val npu = NpuTrialPolicy.Probe(started = false, tokensPerSecond = 40f)
        assertEquals(LocalBackend.CPU, NpuTrialPolicy.choose(cpu, npu))
    }

    @Test
    fun `given a slower NPU when choosing then CPU stays`() {
        val npu = NpuTrialPolicy.Probe(started = true, tokensPerSecond = 9f)
        assertEquals(LocalBackend.CPU, NpuTrialPolicy.choose(cpu, npu))
    }

    @Test
    fun `given an equal NPU when choosing then CPU stays`() {
        val npu = NpuTrialPolicy.Probe(started = true, tokensPerSecond = 10f)
        assertEquals(LocalBackend.CPU, NpuTrialPolicy.choose(cpu, npu))
    }

    @Test
    fun `given a faster NPU that started when choosing then NPU is allowed`() {
        val npu = NpuTrialPolicy.Probe(started = true, tokensPerSecond = 11f)
        assertEquals(LocalBackend.NPU, NpuTrialPolicy.choose(cpu, npu))
    }
}
