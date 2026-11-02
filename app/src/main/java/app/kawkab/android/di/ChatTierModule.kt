package app.kawkab.android.di

import app.kawkab.android.data.decision.AndroidTypedJudge
import app.kawkab.android.domain.decision.TypedJudge
import app.kawkab.android.domain.fastpath.ChatTierRouter
import app.kawkab.android.domain.fastpath.HandsChatTierRouter
import app.kawkab.android.presentation.ui.chat.home.TurnWatchdogConfig
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the chat router that runs a matched command or skill ahead of the pipeline.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChatTierModule {
    /** Spoken app choices persist locally and appear on Growth. */
    @Binds
    abstract fun bindAppChoice(impl: app.kawkab.android.hands.AndroidAppChoice): app.kawkab.android.hands.AppChoice

    /** Real verdict logits, with a conservative spoken-question fallback. */
    @Binds
    abstract fun bindTypedJudge(impl: AndroidTypedJudge): TypedJudge

    /** The router the chat screen uses for tier 1 and tier 2. */
    @Binds
    abstract fun bindChatTierRouter(impl: HandsChatTierRouter): ChatTierRouter

    /** The one-minute limit on a chat turn with no progress (tasks 23h and 23j). */
    @Binds
    abstract fun bindTurnWatchdog(impl: TurnWatchdogConfig.Default): TurnWatchdogConfig
}
