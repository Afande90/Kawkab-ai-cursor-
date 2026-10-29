package app.kawkab.android.domain.safety

/**
 * A yes that came from the device credential prompt (biometric or PIN).
 * A setting cannot supply this. The caller has to have just received it.
 */
fun interface DeviceConfirmation {
    /** True only after the owner passed biometric or device-PIN confirmation. */
    fun confirmed(): Boolean
}

/**
 * The editable package denylist. [replace] refuses a write that has no device confirmation.
 */
class DenylistStore(initial: Set<String>) {
    private val packages = initial.toMutableSet()

    /** The packages currently denied. */
    fun snapshot(): Set<String> = packages.toSet()

    /**
     * Replaces the package set. Throws when [confirmation] is not a fresh device yes.
     */
    fun replace(next: Set<String>, confirmation: DeviceConfirmation) {
        if (!confirmation.confirmed()) {
            error("Editing the denylist needs biometric or device-PIN confirmation.")
        }
        packages.clear()
        packages.addAll(next)
    }
}
