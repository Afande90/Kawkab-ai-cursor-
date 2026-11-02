package app.kawkab.android.hands

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import app.kawkab.android.domain.safety.ActionDenylist
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Calls must use the phone service, with a default-dialer-only fallback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CallRoutingTest {
    private val context = mockk<Context>(relaxed = true)
    private val telecom = mockk<TelecomManager>(relaxed = true)
    private val hands = AndroidPhoneHands(context, ActionDenylist.fromText(""))

    private fun grantPhone() {
        every { context.checkPermission(Manifest.permission.CALL_PHONE, any(), any()) } returns
            PackageManager.PERMISSION_GRANTED
        every { context.getSystemService(TelecomManager::class.java) } returns telecom
    }

    @Test
    fun `a granted call goes through Telecom without an implicit intent`() {
        grantPhone()
        assertEquals("Called +249912345678.", hands.placeCall("+249912345678"))
        verify(exactly = 1) { telecom.placeCall(Uri.fromParts("tel", "+249912345678", null), any<Bundle>()) }
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `a rejected Telecom call targets only the default Phone app`() {
        grantPhone()
        every { telecom.placeCall(any(), any()) } throws SecurityException()
        every { telecom.defaultDialerPackage } returns "com.android.dialer"
        val intent = slot<Intent>()
        every { context.startActivity(capture(intent)) } returns Unit
        hands.placeCall("+249912345678")
        assertEquals(Intent.ACTION_CALL, intent.captured.action)
        assertEquals("com.android.dialer", intent.captured.`package`)
        assertEquals("+249912345678", intent.captured.data?.schemeSpecificPart)
    }

    @Test
    fun `without a default Phone app no implicit call escapes`() {
        grantPhone()
        every { telecom.placeCall(any(), any()) } throws SecurityException()
        every { telecom.defaultDialerPackage } returns null
        assertFalse(hands.placeCall("+249912345678").startsWith("Called"))
        verify(exactly = 0) { context.startActivity(any()) }
    }
}
