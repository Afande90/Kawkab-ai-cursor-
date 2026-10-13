package app.knotwork.android.data.backup

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import app.knotwork.android.data.backup.PreferenceBackupPolicy.Membership
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Writes a committed restore's settings into the app's DataStore when it is first opened after the
 * restart — before anything reads a setting.
 *
 * Settings that travel are replaced by the archive's ([PreferencesBackupJson.decode] keeps only those, and
 * only of their declared type); settings that stay with the phone are kept; settings that reset are
 * cleared. A key the policy does not know is kept.
 *
 * It never throws: a migration that failed would leave the DataStore unable to open at all. A staged entry
 * that cannot be read leaves this phone's settings as they were, and is deleted all the same.
 *
 * @param layout Where the restore is staged.
 */
internal class RestoredPreferencesMigration(private val layout: RestoreLayout) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        layout.isCommitted && layout.stagedPreferences.isFile

    override suspend fun migrate(currentData: Preferences): Preferences {
        val restored = try {
            PreferencesBackupJson.decode(layout.stagedPreferences.readText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "A restore's settings could not be read; this phone's settings are kept")
            return currentData
        }
        return currentData.toMutablePreferences().apply {
            asMap().keys
                .filter { PreferenceBackupPolicy.membershipOf(it.name) != Membership.STAYS }
                .forEach { remove(it) }
            PreferencesBackupJson.writeInto(this, restored)
        }.toPreferences()
    }

    override suspend fun cleanUp() {
        if (!layout.stagedPreferences.delete() && layout.stagedPreferences.exists()) {
            Timber.w("A restore's staged settings could not be deleted")
        }
    }
}
