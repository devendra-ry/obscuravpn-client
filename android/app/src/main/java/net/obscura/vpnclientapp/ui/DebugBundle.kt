package net.obscura.vpnclientapp.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import java.io.File
import kotlin.time.Instant
import net.obscura.lib.util.Logger
import net.obscura.vpnclientapp.BuildConfig
import net.obscura.vpnclientapp.client.ManagerCmd
import net.obscura.vpnclientapp.sharing.DebugBundleFileProvider

private val log = Logger("DebugBundle")

fun collectBundleInfo(context: Context): ManagerCmd.CreateDebugBundle.BundleInfo {
    val memoryInfo =
        runCatching {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
            }
            .onFailure { log.error("failed to get memory info: ${it.message}") }
            .getOrNull()
    val powerManager =
        runCatching { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
            .onFailure { log.error("failed to get power manager: ${it.message}") }
            .getOrNull()
    val uptimeMs = SystemClock.elapsedRealtime()
    return ManagerCmd.CreateDebugBundle.BundleInfo(
        androidSdk = Build.VERSION.SDK_INT,
        appVersion = BuildConfig.VERSION_NAME,
        bootTimestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis() - uptimeMs).toString(),
        brand = Build.BRAND,
        lowPowerMode = powerManager?.isPowerSaveMode,
        model = Build.MODEL,
        osVersionString = Build.VERSION.RELEASE_OR_CODENAME,
        pid = Process.myPid(),
        processName = Application.getProcessName(),
        processorCountActive = Runtime.getRuntime().availableProcessors(),
        processorName = Build.SOC_MODEL,
        ramAvailableGiB = memoryInfo?.let { it.availMem.toDouble() / 1024.0 / 1024.0 / 1024.0 },
        ramLogicalGiB = memoryInfo?.let { it.totalMem.toDouble() / 1024.0 / 1024.0 / 1024.0 },
        thermalState = powerManager?.currentThermalStatus?.let(::thermalLabel),
        uptimeHours = uptimeMs.toDouble() / 3_600_000.0,
    )
}

private fun thermalLabel(status: Int): String =
    when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "none"
        PowerManager.THERMAL_STATUS_LIGHT -> "light"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
        else -> status.toString()
    }

fun launchShareDebugBundle(context: Context, path: String, asEmail: Boolean = false) {
    val authority = "${BuildConfig.APPLICATION_ID}.debug_bundle_file_provider"
    val uri = DebugBundleFileProvider.getUriForFile(context, authority, File(path))
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (asEmail) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf("help@obscura.net"))
                putExtra(Intent.EXTRA_SUBJECT, "Help needed for Obscura VPN (Android, ${BuildConfig.VERSION_NAME})")
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, "Share Debug Bundle"))
}
