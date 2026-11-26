package net.obscura.vpnclientapp.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class PackageMonitor(
    private val context: Context,
    private val settings: () -> AndroidVpnSettings,
    private val onSplitAppInstalled: () -> Unit,
) {
    private var registered = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                val pkg = intent.data?.schemeSpecificPart
                val current = settings()
                if (pkg != null && !replacing && current.splitMode != SplitMode.Off && pkg in current.splitApps) {
                    onSplitAppInstalled()
                }
            }
        }

    fun setEnabled(enabled: Boolean) {
        if (enabled == registered) return
        registered = enabled
        if (enabled) {
            val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.unregisterReceiver(receiver)
        }
    }
}
