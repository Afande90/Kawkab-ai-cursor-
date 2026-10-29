package app.kawkab.android.domain.models

/**
 * Splits phones into flagship and everything smaller for the brain default.
 *
 * Onboarding starts a smaller phone on Gemma 4 E2B. E4B stays in the picker.
 */
object DefaultBrain {
    /** Bytes in one gibibyte of RAM. */
    const val BYTES_PER_GIB: Long = 1_073_741_824L

    /** Total RAM at or above which the phone counts as flagship. */
    const val FLAGSHIP_RAM_BYTES: Long = 16L * BYTES_PER_GIB

    /**
     * Whether [totalRamBytes] is a flagship phone.
     *
     * @param totalRamBytes `ActivityManager.MemoryInfo.totalMem`, or 0 when it cannot be read.
     * @return `true` at or above [FLAGSHIP_RAM_BYTES].
     */
    fun isFlagship(totalRamBytes: Long): Boolean = totalRamBytes >= FLAGSHIP_RAM_BYTES
}
