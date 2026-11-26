package net.obscura.vpnclientapp.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock

private const val DEFAULT_SCREEN_ON_DEBOUNCE_MS = 45_000L

/** Calls [onWake] when Doze ends, or when the screen turns on outside the debounce window. */
class WakeMonitor(
    private val context: Context,
    private val minScreenOnIntervalMs: Long = DEFAULT_SCREEN_ON_DEBOUNCE_MS,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
    private val onWake: () -> Unit,
) {
    internal class WakeGate(private val minScreenOnIntervalMs: Long) {
        private var lastWakeElapsedMs = Long.MIN_VALUE

        @Synchronized
        fun shouldWake(action: String?, deviceIdle: Boolean, nowElapsedMs: Long): Boolean {
            val wake =
                when (action) {
                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> !deviceIdle
                    Intent.ACTION_SCREEN_ON ->
                        lastWakeElapsedMs == Long.MIN_VALUE ||
                            (nowElapsedMs - lastWakeElapsedMs) >= minScreenOnIntervalMs
                    else -> false
                }
            if (wake) {
                lastWakeElapsedMs = nowElapsedMs
            }
            return wake
        }

        @Synchronized
        fun reset() {
            lastWakeElapsedMs = Long.MIN_VALUE
        }
    }

    private val power = context.getSystemService(PowerManager::class.java)
    private val gate = WakeGate(minScreenOnIntervalMs)
    private var registered = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (gate.shouldWake(intent.action, power.isDeviceIdleMode, elapsedRealtimeMs())) {
                    onWake()
                }
            }
        }

    fun setEnabled(enabled: Boolean) {
        if (enabled == registered) return
        registered = enabled
        if (enabled) {
            gate.reset()
            val filter =
                IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED) }
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.unregisterReceiver(receiver)
            gate.reset()
        }
    }
}
