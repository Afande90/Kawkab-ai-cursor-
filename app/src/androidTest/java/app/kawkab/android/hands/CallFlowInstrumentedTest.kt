package app.kawkab.android.hands

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kawkab.android.domain.safety.ActionDenylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 23j on the emulator: duplicate contacts collapse, "him" does not match "Ibrahim", a
 * follow-up chip completes the call, and a call without CALL_PHONE opens the dialer.
 */
@RunWith(AndroidJUnit4::class)
class CallFlowInstrumentedTest {

    private val book = listOf(
        ContactEntry("Abdalla Mubadala", "+249 912 345 678", "Mobile"),
        ContactEntry("Abdalla Mubadala", "0912345678", "Mobile"),
        ContactEntry("Abdalla Osman", "+249 911 000 111", "Work"),
        ContactEntry("Ibrahim Hassan", "+249 933 222 333", "Mobile"),
        ContactEntry("Hashim Musa", "+249 944 333 444", "Mobile"),
    )

    @Test
    fun duplicateContactsCollapse() {
        assertEquals(1, ContactMatcher.match("abdalla mubadala", book).size)
    }

    @Test
    fun himDoesNotMatchIbrahim() {
        assertTrue(ContactMatcher.match("him", book).isEmpty())
    }

    @Test
    fun aFollowUpChipCompletesTheCall() {
        val choice = ContactChoice()
        val choices = ContactMatcher.match("abdalla", book)
        choice.offer("call_phone", emptyMap(), choices, System.currentTimeMillis())
        val tapped = choices.first { it.name == "Abdalla Mubadala" }

        val answer = choice.answer(ContactChip.text(tapped), System.currentTimeMillis())

        assertTrue(answer is ContactAnswer.Picked)
        assertEquals("call_phone", (answer as ContactAnswer.Picked).tool)
        assertEquals(tapped.phone, answer.args["contact"])
    }

    @Test
    fun deniedCallPhoneFallsBackToTheDialer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            "CALL_PHONE is not granted on a fresh emulator install",
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_DENIED,
        )
        val hands = AndroidPhoneHands(context, ActionDenylist.fromText(""))

        assertEquals(CallFallback.DIALER_OPENED, hands.placeCall("+249912345678"))
    }
}
