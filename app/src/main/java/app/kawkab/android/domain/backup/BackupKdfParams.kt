package app.kawkab.android.domain.backup

/**
 * Argon2id cost parameters (RFC 9106) the archive key is derived with.
 *
 * The writer always uses [DEFAULT]. A reader takes the parameters from the archive's header, which is
 * read before anything is authenticated, so [isAcceptable] bounds them: a header asking for more
 * memory or passes than [MAX_MEMORY_KIB] / [MAX_ITERATIONS] / [MAX_PARALLELISM] is refused with
 * [BackupReadFailure.TOO_DEMANDING] instead of being allowed to exhaust the heap or the user's
 * patience.
 *
 * @property memoryKiB Memory cost `m`, in kibibytes.
 * @property iterations Number of passes over the memory, `t`.
 * @property parallelism Number of lanes, `p`. The derivation runs them one after another, so a value
 *   above 1 costs the defender time without slowing an attacker; the writer uses 1.
 */
data class BackupKdfParams(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {

    /**
     * Whether a reader may derive a key with these parameters: each within its bound, and the memory
     * at least the `8 · p` KiB RFC 9106 §3.1 requires.
     *
     * @return `true` when the parameters are within the bounds a reader accepts.
     */
    fun isAcceptable(): Boolean = parallelism in 1..MAX_PARALLELISM &&
        iterations in 1..MAX_ITERATIONS &&
        memoryKiB in MIN_MEMORY_KIB_PER_LANE * parallelism..MAX_MEMORY_KIB

    /** The parameters the writer uses and the bounds a reader accepts. */
    companion object {

        /**
         * What the writer derives with: 64 MiB, three passes, one lane. On the reference phone
         * (Samsung SM-S938B, Android 16) the median derivation takes 1.0 s, the slowest of five 1.9 s;
         * 128 MiB took 3–5 s.
         */
        val DEFAULT: BackupKdfParams = BackupKdfParams(memoryKiB = 65_536, iterations = 3, parallelism = 1)

        /** The most memory a header may ask for: 256 MiB, half the reference phone's app heap. */
        const val MAX_MEMORY_KIB: Int = 262_144

        /** The most passes a header may ask for. */
        const val MAX_ITERATIONS: Int = 10

        /** The most lanes a header may ask for. */
        const val MAX_PARALLELISM: Int = 4

        /** RFC 9106 §3.1: the memory must be at least eight KiB per lane. */
        private const val MIN_MEMORY_KIB_PER_LANE: Int = 8
    }
}
