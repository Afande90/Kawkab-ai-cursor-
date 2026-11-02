package app.kawkab.android.hands

import android.Manifest
import android.telephony.SmsManager
import app.kawkab.android.domain.safety.ActionDenylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Sending must dispatch through Android SMS, without racing a newly opened message composer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DirectSmsTest {
    @Test fun `granted SMS sends the exact recipient and text without opening a composer`() {
        val app = RuntimeEnvironment.getApplication()
        org.robolectric.shadows.ShadowSubscriptionManager.setDefaultSmsSubscriptionId(1)
        shadowOf(app.packageManager).setSystemFeature("android.hardware.telephony", true)
        shadowOf(app.packageManager).setSystemFeature("android.hardware.telephony.messaging", true)
        shadowOf(app).grantPermissions(Manifest.permission.SEND_SMS)
        val hands = AndroidPhoneHands(app, ActionDenylist.fromText(""))
        val result = hands.composeSms("+249912345678", "On my way")
        assertTrue(result, result.contains("submitted"))
        val sent = shadowOf(
            app.getSystemService(SmsManager::class.java).createForSubscriptionId(1),
        ).lastSentTextMessageParams
        assertEquals("+249912345678", sent.destinationAddress)
        assertEquals("On my way", sent.text)
        assertEquals(null, shadowOf(app).nextStartedActivity)
    }
}
