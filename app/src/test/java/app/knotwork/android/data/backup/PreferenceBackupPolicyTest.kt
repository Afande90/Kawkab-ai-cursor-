package app.knotwork.android.data.backup

import app.knotwork.android.architecture.PreferenceKeyCensus
import app.knotwork.android.data.backup.PreferenceBackupPolicy.Membership
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PreferenceBackupPolicy] decides every setting the production code declares, and nothing else — so a
 * new setting does not build until someone says whether it travels.
 */
class PreferenceBackupPolicyTest {

    @Test
    fun `given the key census when compared with the policy then every key is decided once and no other`() {
        // Given
        val declared = PreferenceKeyCensus.names

        // Then
        assertEquals(
            "keys without a decision: ${declared - PreferenceBackupPolicy.decided.keys}; " +
                "decisions for keys no code declares: ${PreferenceBackupPolicy.decided.keys - declared}",
            declared,
            PreferenceBackupPolicy.decided.keys,
        )
    }

    @Test
    fun `given the key census when compared with the travelling types then each is the type the code declares`() {
        // Given
        val declared = PreferenceKeyCensus.declared.toMap()

        // Then
        PreferenceBackupPolicy.decided.filterValues { it == Membership.TRAVELS }.keys.forEach { key ->
            assertEquals(key, declared[key], PreferenceBackupPolicy.typeOf(key)?.wire)
        }
        assertEquals(null, PreferenceBackupPolicy.typeOf("crash_reporting_enabled"))
    }

    @Test
    fun `given a key that does not travel when asked why then it has a reason`() {
        // Then
        PreferenceBackupPolicy.decided.filterValues { it != Membership.TRAVELS }.keys.forEach { key ->
            assertNotNull(key, PreferenceBackupPolicy.reasonFor(key))
        }
    }

    @Test
    fun `given the decisions when counted then they are the recorded ones`() {
        // Then — decisions.md §86 Р3: device-bound keys and the legacy plain token stay; one grant resets.
        val byMembership = PreferenceBackupPolicy.decided.values.groupingBy { it }.eachCount()
        assertEquals(mapOf(Membership.TRAVELS to 58, Membership.STAYS to 10, Membership.RESETS to 1), byMembership)
        assertEquals(Membership.RESETS, PreferenceBackupPolicy.membershipOf("external_automation_enabled"))
        listOf("crash_reporting_enabled", "local_model_backend", "hugging_face_token").forEach {
            assertEquals(it, Membership.STAYS, PreferenceBackupPolicy.membershipOf(it))
        }
    }

    @Test
    fun `given a key the table does not know when asked then it stays`() {
        // Then
        assertTrue(PreferenceBackupPolicy.membershipOf("left_by_an_old_release") == Membership.STAYS)
    }
}
