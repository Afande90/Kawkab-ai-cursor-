package app.kawkab.android.domain.engine

/**
 * Total RAM of the phone, as the platform reports it.
 */
fun interface DeviceMemory {
    /**
     * Total RAM in bytes.
     *
     * @return `ActivityManager.MemoryInfo.totalMem`, or 0 when it cannot be read.
     */
    fun totalRamBytes(): Long
}
