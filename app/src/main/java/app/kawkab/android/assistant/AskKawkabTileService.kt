package app.kawkab.android.assistant

import android.app.PendingIntent
import android.service.quicksettings.TileService

/**
 * The "Ask Kawkab" Quick Settings tile (task 23k): a fallback that works even when the power
 * button is set to the power menu. It opens the assistant panel when Kawkab holds the assistant
 * role, otherwise a new chat.
 */
class AskKawkabTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (KawkabVoiceInteractionService.showPanel()) return
        val pending = PendingIntent.getActivity(
            this,
            0,
            AssistantEntry.newChatIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        startActivityAndCollapse(pending)
    }
}
