package app.kawkab.android.di

import android.content.Context
import app.kawkab.android.domain.safety.ActionDenylist
import app.kawkab.android.domain.safety.ActionRunner
import app.kawkab.android.domain.safety.ActivityLog
import app.kawkab.android.domain.safety.FileActivityLog
import app.kawkab.android.domain.safety.OutboundTapGuard
import app.kawkab.android.domain.safety.ScreenWatch
import app.kawkab.android.hands.AndroidContactBook
import app.kawkab.android.hands.AndroidPhoneHands
import app.kawkab.android.hands.ContactBook
import app.kawkab.android.hands.PhoneHands
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * Wires the on-device safety checks and the phone hands.
 * The activity log is a private file. Nothing in this module sends it anywhere.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SafetyModule {
    /** The contact book the call and message tools use. */
    @Binds
    @Singleton
    abstract fun bindContactBook(impl: AndroidContactBook): ContactBook

    /** Intents and accessibility taps. */
    @Binds
    @Singleton
    abstract fun bindPhoneHands(impl: AndroidPhoneHands): PhoneHands

    companion object {
        /** Patterns shipped in the APK, the same list as `safety/outbound_patterns.txt`. */
        @Provides
        @Singleton
        fun provideTapGuard(@ApplicationContext context: Context): OutboundTapGuard {
            val text = context.assets.open("safety/outbound_patterns.txt").bufferedReader().readText()
            return OutboundTapGuard.fromText(text)
        }

        /** Packages shipped in the APK, the same list as `safety/package_denylist.txt`. */
        @Provides
        @Singleton
        fun provideDenylist(@ApplicationContext context: Context): ActionDenylist {
            val text = context.assets.open("safety/package_denylist.txt").bufferedReader().readText()
            return ActionDenylist.fromText(text)
        }

        /** Private JSON-lines log under the app files directory. */
        @Provides
        @Singleton
        fun provideActivityLog(@ApplicationContext context: Context): ActivityLog =
            FileActivityLog(File(context.filesDir, "activity-log.jsonl"))

        /** The gate every hands action goes through. */
        @Provides
        @Singleton
        fun provideActionRunner(denylist: ActionDenylist, log: ActivityLog, screen: ScreenWatch): ActionRunner =
            ActionRunner(denylist, log, screen)
    }
}
