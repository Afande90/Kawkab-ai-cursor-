package app.knotwork.android.data.services

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import app.knotwork.android.domain.constants.NotificationChannels
import app.knotwork.android.domain.constants.NotificationIds
import app.knotwork.android.domain.models.ModelDownloadFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

/**
 * Covers the notice that a model download stopped: which stops post one, what it says, that each
 * file has its own slot in the registry's family, that it goes when asked, and that nothing is
 * posted without the permission.
 */
@RunWith(RobolectricTestRunner::class)
class ModelDownloadOutcomeNotifierTest {

    private lateinit var context: Context
    private lateinit var notifier: ModelDownloadOutcomeNotifier

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier = ModelDownloadOutcomeNotifier(context)
    }

    @Test
    fun `given a pause when announced then the notice names the file on the download channel`() {
        notifier.notifyStopped("gemma.litertlm", ModelDownloadFailure.PAUSED_TIME_LIMIT)

        val shadow = Shadows.shadowOf(notificationManager())
        val posted = shadow.getNotification(NotificationIds.Family.MODEL_DOWNLOAD_OUTCOME.idFor("gemma.litertlm"))
        assertNotNull("the notice sits in the file's slot of its family", posted)
        assertEquals(NotificationChannels.MODEL_DOWNLOAD, posted.channelId)
        assertEquals("Model download paused", Shadows.shadowOf(posted).contentTitle)
        assertTrue(Shadows.shadowOf(posted).contentText.toString().startsWith("gemma.litertlm: "))
        // The download channel may not exist yet: a download that pauses before it enters the
        // foreground never posted its ongoing notification.
        assertNotNull(notificationManager().getNotificationChannel(NotificationChannels.MODEL_DOWNLOAD))
    }

    @Test
    fun `given a checksum mismatch when announced then the notice says the file was deleted`() {
        notifier.notifyStopped("gemma.litertlm", ModelDownloadFailure.CHECKSUM_MISMATCH)

        val posted = Shadows.shadowOf(notificationManager())
            .getNotification(NotificationIds.Family.MODEL_DOWNLOAD_OUTCOME.idFor("gemma.litertlm"))
        assertEquals("Downloaded model did not match", Shadows.shadowOf(posted).contentTitle)
        assertTrue(Shadows.shadowOf(posted).contentText.toString().contains("so it was deleted"))
    }

    @Test
    fun `given any other failure when announced then nothing is posted`() {
        notifier.notifyStopped("gemma.litertlm", ModelDownloadFailure.OTHER)

        assertEquals(0, Shadows.shadowOf(notificationManager()).size())
    }

    @Test
    fun `given stops of two files when announced then each keeps its own notice`() {
        notifier.notifyStopped("a.litertlm", ModelDownloadFailure.PAUSED_TIME_LIMIT)
        notifier.notifyStopped("b.litertlm", ModelDownloadFailure.CHECKSUM_MISMATCH)

        assertEquals(2, Shadows.shadowOf(notificationManager()).size())
    }

    @Test
    fun `given a notice when dismissed then only that file's notice goes`() {
        notifier.notifyStopped("a.litertlm", ModelDownloadFailure.PAUSED_TIME_LIMIT)
        notifier.notifyStopped("b.litertlm", ModelDownloadFailure.PAUSED_TIME_LIMIT)

        notifier.dismiss("a.litertlm")

        val shadow = Shadows.shadowOf(notificationManager())
        assertEquals(1, shadow.size())
        assertNotNull(shadow.getNotification(NotificationIds.Family.MODEL_DOWNLOAD_OUTCOME.idFor("b.litertlm")))
    }

    @Test
    fun `given notifications are not allowed when a stop is announced then nothing is posted`() {
        Shadows.shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.notifyStopped("gemma.litertlm", ModelDownloadFailure.PAUSED_TIME_LIMIT)

        assertEquals(0, Shadows.shadowOf(notificationManager()).size())
    }

    private fun notificationManager(): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
