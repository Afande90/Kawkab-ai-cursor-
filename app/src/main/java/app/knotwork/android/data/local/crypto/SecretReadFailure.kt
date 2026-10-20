package app.knotwork.android.data.local.crypto

import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

/**
 * A stored blob that no cipher call can ever open: the Keystore holds no key under its alias, or
 * the blob is too short to be one the cipher wrote. Thrown by the cipher so the store can tell
 * these from a Keystore that fails for now ([SecretReadFailure]).
 *
 * @param message What makes the blob unopenable.
 */
class UnopenableBlobException(message: String) : GeneralSecurityException(message)

/**
 * Tells a read failure that will last from one that may pass.
 *
 * A caller that drops a secret on a failure that would have passed loses it for good, though it
 * was stored intact — an API key lost to a Keystore that was busy after an OS update. So a
 * failure counts as lasting only when its cause chain says the stored value or its key is
 * unusable:
 *
 *  - [AEADBadTagException]: the blob does not authenticate under this key and slot;
 *  - [UnopenableBlobException]: no key under the alias, or a blob too short to be one;
 *  - [KeyPermanentlyInvalidatedException]: the platform invalidated the key;
 *  - an `android.security.KeyStoreException` (API 33) whose code is `ERROR_KEY_DOES_NOT_EXIST` or
 *    `ERROR_KEY_CORRUPTED`.
 *
 * Anything else counts as passing — a `ProviderException` or `UnrecoverableKeyException` (the
 * platform wraps its system errors in both), `java.security.KeyStoreException`, any other
 * platform code, an error not seen before: the secret stays stored and is read again later.
 * The platform's own `isTransientFailure` is not enough to drop a secret on: it is `false` for
 * codes that say nothing about the key, such as a denied permission or a failing KeyMint.
 */
object SecretReadFailure {

    /** Causes looked at before giving up: a guard against a cyclic chain, far above real depths. */
    private const val MAX_CAUSE_DEPTH = 16

    /** Platform codes that say the key itself is unusable. */
    private val KEY_UNUSABLE_CODES = setOf(
        android.security.KeyStoreException.ERROR_KEY_DOES_NOT_EXIST,
        android.security.KeyStoreException.ERROR_KEY_CORRUPTED,
    )

    /**
     * Whether [error] means the stored value can never be read again.
     *
     * @param error What the read threw.
     * @return `true` for a failure that will last; `false` for one that may pass, or is not known.
     */
    fun isPermanent(error: Throwable): Boolean =
        generateSequence(error) { current -> current.cause?.takeUnless { it === current } }
            .take(MAX_CAUSE_DEPTH)
            .any { cause ->
                cause is AEADBadTagException ||
                    cause is UnopenableBlobException ||
                    cause is KeyPermanentlyInvalidatedException ||
                    (cause is android.security.KeyStoreException && cause.numericErrorCode in KEY_UNUSABLE_CODES)
            }
}
