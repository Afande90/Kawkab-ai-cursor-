package app.knotwork.android.data.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.knotwork.android.R
import app.knotwork.android.domain.constants.NotificationChannels
import app.knotwork.android.domain.constants.NotificationIds
import app.knotwork.android.domain.models.ModelDownloadFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the user, by a notification, that a model download stopped without a model.
 *
 * Only for the two stops nobody would otherwise learn of: a pause at the background time limit,
 * which comes after hours out of sight, and a file that failed its checksum, which comes at the
 * very end of a transfer the user has usually left. The ongoing download notification disappears
 * with its worker either way; without this one the shade would simply go quiet.
 *
 * One notification per file ([NotificationIds.Family.MODEL_DOWNLOAD_OUTCOME], keyed by the file
 * name), on the model-download channel. Tapping it opens the app as its launcher icon does; the
 * Models screen says the same thing the next time it is opened while no other download runs (and
 * offers Continue for a pause), unless setup's Download step or the model's page in Discover said
 * it first. It is removed when
 * the download starts again, is cancelled, or a screen has shown why it stopped. Without the
 * notification permission nothing is posted; the screens still say it.
 *
 * @property context Application context the notification is posted from.
 */
@Singleton
class ModelDownloadOutcomeNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Posts why the download of [fileName] stopped, replacing an earlier notice for the same file.
     *
     * @param fileName Local file name the download was started with.
     * @param failure Why it stopped; anything but a pause or a checksum mismatch posts nothing.
     */
    @SuppressLint("MissingPermission") // hasPostNotificationsPermission() gates the call below.
    fun notifyStopped(fileName: String, failure: ModelDownloadFailure) {
        val (titleRes, textRes) = when (failure) {
            ModelDownloadFailure.PAUSED_TIME_LIMIT ->
                R.string.notifications_model_download_paused_title to R.string.notifications_model_download_paused_text
            ModelDownloadFailure.CHECKSUM_MISMATCH ->
                R.string.notifications_model_download_mismatch_title to
                    R.string.notifications_model_download_mismatch_text
            ModelDownloadFailure.OTHER -> return
        }
        if (!hasPostNotificationsPermission()) return
        ModelDownloadChannel.ensure(context)
        val text = context.getString(textRes, fileName)
        val notification = NotificationCompat.Builder(context, NotificationChannels.MODEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_agent)
            .setContentTitle(context.getString(titleRes))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(AgentForegroundNotification.launchContentIntent(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(idFor(fileName), notification)
    }

    /**
     * Removes the notice for [fileName], if one is showing.
     *
     * @param fileName Local file name the download was started with.
     */
    fun dismiss(fileName: String) {
        NotificationManagerCompat.from(context).cancel(idFor(fileName))
    }

    private fun idFor(fileName: String): Int = NotificationIds.Family.MODEL_DOWNLOAD_OUTCOME.idFor(fileName)

    private fun hasPostNotificationsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

/**
 * The model-download notification channel, created where it is first needed — by the download's
 * own foreground notification, or by a notice that one stopped (which can come first: a download
 * that pauses before it enters the foreground posts no ongoing notification).
 */
internal object ModelDownloadChannel {

    /**
     * Registers the channel; `createNotificationChannel` is idempotent.
     *
     * @param context Context the channel is registered from.
     */
    fun ensure(context: Context) {
        val channel = NotificationChannelCompat.Builder(
            NotificationChannels.MODEL_DOWNLOAD,
            NotificationManager.IMPORTANCE_LOW,
        )
            .setName(context.getString(R.string.notifications_model_download_channel_name))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }
}
