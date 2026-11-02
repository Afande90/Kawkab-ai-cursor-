package app.kawkab.android.domain.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tasks 32 and 33: the crash log keeps frames without messages, five at most, and decides safe mode. */
class CrashRulesTest {
    @Test
    fun `a trace keeps classes and frames but never the message`() {
        val error = IllegalStateException("call Omar about the rent", RuntimeException("secret note"))
        val trace = CrashRules.trace(error)
        assertTrue(trace.startsWith("java.lang.IllegalStateException"))
        assertTrue(trace.contains("Caused by: java.lang.RuntimeException"))
        assertTrue(trace.contains("    at "))
        assertFalse(trace.contains("Omar"))
        assertFalse(trace.contains("secret"))
    }

    @Test
    fun `a native tombstone is cut to its backtrace`() {
        val raw = """
            Cmdline: app.kawkab.android:wake
            signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x8
                x0  0000000000000001  x1  0000000000000000
                  #00 pc 00000000014bb1c8  /data/app/lib/arm64/libonnxruntime.so
                  #01 pc 000000000006da30  /system/lib64/libc.so (pthread_once+148)
            memory map (1234 entries):
        """.trimIndent()
        assertEquals(
            "signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x8\n" +
                "#00 pc 00000000014bb1c8  /data/app/lib/arm64/libonnxruntime.so\n" +
                "#01 pc 000000000006da30  /system/lib64/libc.so (pthread_once+148)",
            CrashRules.nativeTrace(raw),
        )
    }

    @Test
    fun `only the last five crashes are kept, newest first`() {
        var log = emptyList<CrashRecord>()
        (1..7).forEach { log = CrashRules.add(log, CrashRecord(it.toLong(), "java", "t$it")) }
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), log.map { it.atEpochMs })
    }

    @Test
    fun `a crash within ten seconds of a start makes the next start safe`() {
        assertTrue(CrashRules.safeMode(lastStartMs = 1_000, lastCrashMs = 9_000))
        assertFalse(CrashRules.safeMode(lastStartMs = 1_000, lastCrashMs = 20_000))
        assertFalse(CrashRules.safeMode(lastStartMs = 10_000, lastCrashMs = 9_000))
        assertFalse(CrashRules.safeMode(lastStartMs = null, lastCrashMs = 9_000))
        assertFalse(CrashRules.safeMode(lastStartMs = 1_000, lastCrashMs = null))
    }

    @Test
    fun `the report names the app, the phone and each crash`() {
        val report = CrashRules.report(
            "1.0 (abc1234)",
            "Android 16 (API 36), Nothing A059",
            listOf(CrashRecord(5, "native", "#00 pc 1")),
        )
        assertTrue(report.contains("App: 1.0 (abc1234)"))
        assertTrue(report.contains("Device: Android 16"))
        assertTrue(report.contains("#1 native at 5"))
        assertTrue(CrashRules.report("v", "d", emptyList()).contains("No crashes recorded."))
    }
}
