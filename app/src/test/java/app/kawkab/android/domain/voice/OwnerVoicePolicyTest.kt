package app.kawkab.android.domain.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner voice is mandatory; missing, silent, invalid and different speakers cannot authorize commands. */
class OwnerVoicePolicyTest {
    @Test fun missingProfileRefusesSpeech() {
        assertFalse(OwnerVoicePolicy.matches(floatArrayOf(1f, 0f), null))
    }

    @Test fun sameSpeakerMatches() {
        assertTrue(OwnerVoicePolicy.matches(floatArrayOf(1f, 0.05f), floatArrayOf(1f, 0f)))
    }

    @Test fun differentSpeakerIsIgnored() {
        assertFalse(OwnerVoicePolicy.matches(floatArrayOf(0f, 1f), floatArrayOf(1f, 0f)))
    }

    @Test fun silentOrInvalidVectorsNeverMatch() {
        assertFalse(OwnerVoicePolicy.matches(floatArrayOf(0f, 0f), floatArrayOf(1f, 0f)))
        assertFalse(OwnerVoicePolicy.matches(floatArrayOf(Float.NaN, 1f), floatArrayOf(1f, 0f)))
        assertFalse(OwnerVoicePolicy.matches(floatArrayOf(1f), floatArrayOf(1f, 0f)))
    }

    @Test fun enrollmentRequiresTenWakeWordsAndTwoSentences() {
        assertFalse(OwnerVoicePolicy.enrollmentComplete(10))
        assertTrue(OwnerVoicePolicy.enrollmentComplete(12))
    }

    @Test fun trimKeepsTheSpokenPartOfALongNoisyClip() {
        val clip = FloatArray(48_000) { if (it % 7 == 0) 0.001f else 0f }
        for (i in 20_000 until 28_000) clip[i] = if (i % 2 == 0) 0.4f else -0.4f
        val trimmed = OwnerVoicePolicy.trimSpeech(clip)
        assertTrue(trimmed.size in 16_000 until 48_000)
        assertTrue(OwnerVoicePolicy.usable(trimmed))
        assertTrue(trimmed.count { it == 0.4f } == 4_000)
    }

    @Test fun enrollmentDropsTwoNoisyTakesButRejectsTwoVoices() {
        val owner = List(10) { floatArrayOf(1f, 0.1f * (it % 3), 0f) }
        val noisy = listOf(floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f))
        val profile = OwnerVoicePolicy.enrollmentProfile(owner + noisy)
        assertTrue(profile != null && OwnerVoicePolicy.matches(floatArrayOf(1f, 0.1f, 0f), profile))
        val twoVoices = List(6) { floatArrayOf(1f, 0f, 0f) } + List(6) { floatArrayOf(0f, 1f, 0f) }
        assertTrue(OwnerVoicePolicy.enrollmentProfile(twoVoices) == null)
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
