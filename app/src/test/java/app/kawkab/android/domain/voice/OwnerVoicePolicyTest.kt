package app.kawkab.android.domain.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner voice is mandatory; missing, silent, invalid and different speakers cannot authorize commands. */
class OwnerVoicePolicyTest {
    @Test fun missingProfileRefusesSpeech() { assertFalse(OwnerVoicePolicy.matches(floatArrayOf(1f, 0f), null)) }
    @Test fun sameSpeakerMatches() { assertTrue(OwnerVoicePolicy.matches(floatArrayOf(1f, 0.05f), floatArrayOf(1f, 0f))) }
    @Test fun differentSpeakerIsIgnored() { assertFalse(OwnerVoicePolicy.matches(floatArrayOf(0f, 1f), floatArrayOf(1f, 0f))) }
    @Test fun silentOrInvalidVectorsNeverMatch() {
        assertFalse(OwnerVoicePolicy.matches(floatArrayOf(0f, 0f), floatArrayOf(1f, 0f)))
        assertFalse(OwnerVoicePolicy.matches(floatArrayOf(Float.NaN, 1f), floatArrayOf(1f, 0f)))
        assertFalse(OwnerVoicePolicy.matches(floatArrayOf(1f), floatArrayOf(1f, 0f)))
    }
    @Test fun enrollmentRequiresTenWakeWordsAndTwoSentences() {
        assertFalse(OwnerVoicePolicy.enrollmentComplete(10))
        assertTrue(OwnerVoicePolicy.enrollmentComplete(12))
    }
    @Test fun aSpokenYesIsFreshAndSingleUse() {
        SpokenOwnerAnswer.record("yes", 100)
        assertTrue(SpokenOwnerAnswer.consumeYes(101))
        assertFalse(SpokenOwnerAnswer.consumeYes(102))
        SpokenOwnerAnswer.record("yes", 100)
        assertFalse(SpokenOwnerAnswer.consumeYes(10_000))
        SpokenOwnerAnswer.record("yes but not now", 100)
        assertFalse(SpokenOwnerAnswer.consumeYes(101))
    }
}
