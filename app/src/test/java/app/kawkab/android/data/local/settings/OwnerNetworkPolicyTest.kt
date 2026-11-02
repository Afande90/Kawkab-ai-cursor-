package app.kawkab.android.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.firstArg
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/** Advanced legacy switches cannot enable off-phone model processing. */
class OwnerNetworkPolicyTest {
    @Test fun `the owner processing rule stays enabled after an attempt to turn it off`() = runTest {
        val values = MutableStateFlow<Preferences>(emptyPreferences())
        val data = mockk<DataStore<Preferences>>()
        every { data.data } returns values
        coEvery { data.updateData(any()) } coAnswers {
            firstArg<suspend (Preferences) -> Preferences>()(values.value).also { values.value = it }
        }
        val store = NetworkSettingsStore(data)
        store.setBlockNetworkFromLocalModel(false)
        assertTrue(store.blockNetworkFromLocalModel.first())
    }
}
