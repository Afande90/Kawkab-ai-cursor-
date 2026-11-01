package app.kawkab.android.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Task 27: the wake word's gate, speech window, counters and keyword file. */
class WakeWordTest {
    @Test
    fun `a wake inside the refractory time or during a conversation is dropped`() {
        val gate = WakeGate(refractoryMs = 2_000)
        assertTrue(gate.hit(10_000, busy = false))
        assertFalse(gate.hit(11_000, busy = false))
        assertFalse(gate.hit(13_000, busy = true))
        assertTrue(gate.hit(12_500, busy = false))
    }

    @Test
    fun `silence reaches the spotter only as pre-roll when speech starts`() {
        val window = SpeechWindow(preRollFrames = 2, hangoverFrames = 1)
        val frames = (0 until 6).map { floatArrayOf(it.toFloat()) }
        assertTrue(window.feed(frames[0], speech = false).isEmpty())
        assertTrue(window.feed(frames[1], speech = false).isEmpty())
        assertTrue(window.feed(frames[2], speech = false).isEmpty())
        val start = window.feed(frames[3], speech = true)
        assertEquals(listOf(1f, 2f, 3f), start.map { it[0] })
        assertEquals(listOf(4f), window.feed(frames[4], speech = false).map { it[0] })
        assertTrue(window.feed(frames[5], speech = false).isEmpty())
    }

    @Test
    fun `reset forgets the pre-roll`() {
        val window = SpeechWindow(preRollFrames = 4, hangoverFrames = 0)
        window.feed(floatArrayOf(1f), speech = false)
        window.reset()
        assertEquals(listOf(2f), window.feed(floatArrayOf(2f), speech = true).map { it[0] })
    }

    @Test
    fun `counters wait a quarter hour and refuse battery numbers across a charge`() {
        val hour = 3_600_000L
        val stats = WakeStats(startedAtMs = 0, startBatteryPercent = 90, wakes = 3)
        assertNull(stats.wakesPerHour(hour / 10))
        assertEquals(1.0, stats.wakesPerHour(3 * hour)!!, 1e-9)
        assertEquals(2.0, stats.batteryPerHour(3 * hour, batteryPercent = 84, charging = false)!!, 1e-9)
        assertNull(stats.batteryPerHour(3 * hour, batteryPercent = 84, charging = true))
        assertNull(stats.batteryPerHour(3 * hour, batteryPercent = 95, charging = false))
    }

    @Test
    fun `the shipped keyword file matches the list and uses only model tokens`() {
        val dir = File("src/main/assets/wake")
        val lines = File(dir, "keywords.txt").readLines().filter { it.isNotBlank() }
        assertEquals(WakeWord.KEYWORD_LINES, lines)
        val tokens = File(dir, "tokens.txt").readLines().map { it.substringBeforeLast(' ') }.toSet()
        lines.forEach { line ->
            assertTrue(line, line.endsWith(" @${WakeWord.TAG}"))
            line.substringBefore(" @").split(' ').forEach { token -> assertTrue("$line: $token", token in tokens) }
        }
    }

    @Test
    fun `every wake model file ships`() {
        val names = File("src/main/assets/wake").list()!!.toSet()
        listOf(
            "encoder.int8.onnx",
            "decoder.int8.onnx",
            "joiner.int8.onnx",
            "tokens.txt",
            "keywords.txt",
            "silero_vad.onnx",
        )
            .forEach { assertTrue(it, it in names) }
    }
}
