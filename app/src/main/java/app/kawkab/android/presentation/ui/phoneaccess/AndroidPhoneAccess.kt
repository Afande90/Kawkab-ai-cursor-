package app.kawkab.android.presentation.ui.phoneaccess

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.kawkab.android.domain.phoneaccess.PhoneAccessItem
import app.kawkab.android.domain.phoneaccess.PhoneAccessLine
import app.kawkab.android.domain.phoneaccess.PhoneAccessStatus

/**
 * Reads the phone's current grants. The screen shows one status line per item.
 */
object AndroidPhoneAccess {
    /**
     * Intent extra carrying the permission a command still needs.
     */
    const val PERMISSION_EXTRA: String = "app.kawkab.android.extra.PHONE_ACCESS_PERMISSION"

    /**
     * Whether each item is on right now. A read that throws is off, never hidden.
     */
    fun lines(context: Context): List<PhoneAccessLine> = runCatching { read(context) }
        .getOrElse { PhoneAccessStatus.lines(emptyMap()) }

    /**
     * Opens the system accessibility settings.
     */
    fun accessibilitySettings(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Asks Android to ignore battery optimisation for this app.
     * The owner opens this dialog from Phone access. The Play-policy lint
     * check is suppressed because the exemption is that tap, not a silent one.
     */
    @SuppressLint("BatteryLife")
    fun batterySettings(context: Context): Intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = "package:${context.packageName}".toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun read(context: Context): List<PhoneAccessLine> {
        val on = PhoneAccessItem.entries.associateWith { item ->
            when (item) {
                PhoneAccessItem.ACCESSIBILITY -> PhoneAccessStatus.serviceOn(
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                    PhoneAccessStatus.ACCESSIBILITY_SERVICE,
                )
                PhoneAccessItem.BATTERY -> {
                    val power = context.getSystemService(PowerManager::class.java)
                    power?.isIgnoringBatteryOptimizations(context.packageName) == true
                }
                else -> granted(context, PhoneAccessStatus.permission(item))
            }
        }
        return PhoneAccessStatus.lines(on)
    }

    private fun granted(context: Context, permission: String?): Boolean = permission != null &&
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
