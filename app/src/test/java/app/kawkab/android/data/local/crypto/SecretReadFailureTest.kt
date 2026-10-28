package app.kawkab.android.data.local.crypto

import android.security.keystore.KeyPermanentlyInvalidatedException
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException

/**
 * Unit coverage for [SecretReadFailure].
 *
 * A failure classified as lasting makes the caller drop a stored secret, so every class is pinned
 * here: the ones that mean the value or its key is unusable, and the ones that may pass — which
 * must keep the secret.
 */
class SecretReadFailureTest {

    /** A platform Keystore error with [code], at the end of its chain. */
    private fun platform(code: Int): android.security.KeyStoreException = mockk {
        every { numericErrorCode } returns code
        every { cause } returns null
    }

    @Test
    fun `given a tag that does not verify when classified then it lasts`() {
        assertTrue(SecretReadFailure.isPermanent(AEADBadTagException("mac check in GCM failed")))
    }

    @Test
    fun `given no key under the alias or a short blob when classified then it lasts`() {
        assertTrue(SecretReadFailure.isPermanent(UnopenableBlobException("No Keystore key under alias 'a'")))
    }

    @Test
    fun `given an invalidated key when classified then it lasts`() {
        val invalidated = mockk<KeyPermanentlyInvalidatedException> { every { cause } returns null }

        assertTrue(SecretReadFailure.isPermanent(invalidated))
    }

    @Test
    fun `given the platform says the key is gone or corrupted when classified then it lasts`() {
        val gone = ProviderException("Keystore operation failed", platform(KEY_DOES_NOT_EXIST))
        val corrupted = ProviderException("Keystore operation failed", platform(KEY_CORRUPTED))

        assertTrue(SecretReadFailure.isPermanent(gone))
        assertTrue(SecretReadFailure.isPermanent(corrupted))
    }

    @Test
    fun `given a platform code that says nothing about the key when classified then it may pass`() {
        // isTransientFailure is false for these, yet the key itself may be fine.
        val denied = ProviderException("Keystore operation failed", platform(PERMISSION_DENIED))
        val keyMint = ProviderException("Keystore operation failed", platform(KEYMINT_FAILURE))
        val system = ProviderException("Keystore operation failed", platform(INTERNAL_SYSTEM_ERROR))

        assertFalse(SecretReadFailure.isPermanent(denied))
        assertFalse(SecretReadFailure.isPermanent(keyMint))
        assertFalse(SecretReadFailure.isPermanent(system))
    }

    @Test
    fun `given a Keystore failure with no verdict when classified then it may pass`() {
        assertFalse(SecretReadFailure.isPermanent(ProviderException("Keystore operation failed")))
        assertFalse(SecretReadFailure.isPermanent(java.security.KeyStoreException("Keystore not initialized")))
        // The platform wraps its system errors in this one too.
        assertFalse(SecretReadFailure.isPermanent(UnrecoverableKeyException("Failed to obtain information about key")))
        assertFalse(SecretReadFailure.isPermanent(IllegalStateException("not seen before")))
    }

    @Test
    fun `given a lasting cause deep in the chain when classified then it lasts`() {
        val wrapped = IllegalStateException("outer", ProviderException("middle", AEADBadTagException("inner")))

        assertTrue(SecretReadFailure.isPermanent(wrapped))
    }

    @Test
    fun `given a cyclic cause chain when classified then it ends and may pass`() {
        val first = IllegalStateException("first")
        val second = IllegalStateException("second", first)
        first.initCause(second)

        assertFalse(SecretReadFailure.isPermanent(first))
    }

    private companion object {
        const val KEY_DOES_NOT_EXIST = android.security.KeyStoreException.ERROR_KEY_DOES_NOT_EXIST
        const val KEY_CORRUPTED = android.security.KeyStoreException.ERROR_KEY_CORRUPTED
        const val PERMISSION_DENIED = android.security.KeyStoreException.ERROR_PERMISSION_DENIED
        const val KEYMINT_FAILURE = android.security.KeyStoreException.ERROR_KEYMINT_FAILURE
        const val INTERNAL_SYSTEM_ERROR = android.security.KeyStoreException.ERROR_INTERNAL_SYSTEM_ERROR
    }
}
