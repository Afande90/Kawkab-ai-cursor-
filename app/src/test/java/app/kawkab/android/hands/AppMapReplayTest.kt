package app.kawkab.android.hands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stored app maps replay without a model, check the screen, and still stop for an outbound tap.
 */
class AppMapReplayTest {
    @Test
    fun `the six apps each have a stored map and an open macro`() {
        val apps = BundledAppMaps.maps.map { it.app }
        assertEquals(listOf("Phone", "Messages", "Clock", "Calendar", "WhatsApp", "Gmail"), apps)
        listOf("phone.open", "messages.open", "clock.open", "calendar.open", "whatsapp.open", "gmail.open")
            .forEach { id -> assertTrue(id, BundledAppMaps.find(id) != null) }
        assertNull(BundledAppMaps.find("missing"))
    }

    @Test
    fun `an outbound step waits for a tap and does not run`() {
        val (map, macro) = BundledAppMaps.find("phone.call_mom")!!
        val ran = mutableListOf<String>()
        val outcome = AppMapReplay.play(
            map = map,
            macro = macro,
            confirmed = false,
            readScreen = { "Keypad" },
            run = { ran += it.tool },
        )
        assertEquals(listOf("open_app"), ran)
        assertTrue(outcome is MapReplay.NeedsTap)
        assertEquals("call_phone", (outcome as MapReplay.NeedsTap).tool)
    }

    @Test
    fun `a matching screen replays every step`() {
        val (map, macro) = BundledAppMaps.find("clock.alarm_6")!!
        val ran = mutableListOf<String>()
        val outcome = AppMapReplay.play(
            map = map,
            macro = macro,
            confirmed = false,
            readScreen = { "Alarm" },
            run = { ran += it.tool },
        )
        assertEquals(listOf("open_app", "set_alarm"), ran)
        assertEquals(MapReplay.Ran(2), outcome)
    }

    @Test
    fun `a mismatched screen stops and falls back`() {
        val (map, macro) = BundledAppMaps.find("whatsapp.note")!!
        val ran = mutableListOf<String>()
        val outcome = AppMapReplay.play(
            map = map,
            macro = macro,
            confirmed = true,
            readScreen = { "Home screen" },
            run = { ran += it.tool },
        )
        assertEquals(listOf("open_app"), ran)
        assertTrue(outcome is MapReplay.TierThree)
        assertEquals(0, (outcome as MapReplay.TierThree).atStep)
    }
}
