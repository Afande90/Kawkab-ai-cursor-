package app.kawkab.android.data.engine

import android.app.ActivityManager
import android.content.Context
import app.kawkab.android.domain.engine.DeviceMemory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Reads total RAM from [ActivityManager].
 *
 * A missing service reports 0, which onboarding treats as a small phone.
 */
class AndroidDeviceMemory @Inject constructor(@ApplicationContext private val context: Context) : DeviceMemory {
    override fun totalRamBytes(): Long {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return 0L
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.totalMem
    }
}
