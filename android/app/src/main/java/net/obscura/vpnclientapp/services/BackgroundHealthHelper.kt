package net.obscura.vpnclientapp.services

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

data class BackgroundHealthStatus(
    val ignoringBatteryOptimizations: Boolean,
    val backgroundRestricted: Boolean,
    val isXiaomiFamily: Boolean,
)

object BackgroundHealthHelper {
    private val XIAOMI_BRANDS = setOf("xiaomi", "redmi", "poco", "blackshark")

    fun query(context: Context): BackgroundHealthStatus {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val activityManager = context.getSystemService(ActivityManager::class.java)
        return BackgroundHealthStatus(
            ignoringBatteryOptimizations = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true,
            backgroundRestricted = activityManager?.isBackgroundRestricted == true,
            isXiaomiFamily = isXiaomiFamily(Build.MANUFACTURER, Build.BRAND),
        )
    }

    internal fun isXiaomiFamily(manufacturer: String?, brand: String?): Boolean {
        val m = manufacturer?.trim()?.lowercase().orEmpty()
        val b = brand?.trim()?.lowercase().orEmpty()
        return m in XIAOMI_BRANDS || b in XIAOMI_BRANDS
    }

    fun openBatterySettings(context: Context) {
        val candidates = buildList {
            if (isXiaomiFamily(Build.MANUFACTURER, Build.BRAND)) {
                val label = context.applicationInfo.loadLabel(context.packageManager).toString()
                add(
                    Intent().apply {
                        component =
                            ComponentName(
                                "com.miui.powerkeeper",
                                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                            )
                        putExtra("package_name", context.packageName)
                        putExtra("package_label", label)
                    },
                )
            }
            add(appDetailsIntent(context.packageName))
            add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        launchFirstResolvable(context, candidates)
    }

    fun openXiaomiAutostartSettings(context: Context) {
        val candidates =
            listOf(
                Intent().apply {
                    component =
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity",
                        )
                },
                appDetailsIntent(context.packageName),
            )
        launchFirstResolvable(context, candidates)
    }

    fun openVpnSystemSettings(context: Context) {
        launchFirstResolvable(context, listOf(Intent(Settings.ACTION_VPN_SETTINGS)))
    }

    private fun appDetailsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

    private fun launchFirstResolvable(context: Context, intents: List<Intent>) {
        val pm = context.packageManager
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null) {
                if (runCatching { context.startActivity(intent) }.isSuccess) {
                    return
                }
            }
        }
    }
}
