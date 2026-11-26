package net.obscura.vpnclientapp.services

import android.content.Intent
import android.os.PowerManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeMonitorTest {
    @Test
    fun screenOnIsDebouncedUntilIntervalElapses() {
        val gate = WakeMonitor.WakeGate(minScreenOnIntervalMs = 45_000L)

        assertTrue(gate.shouldWake(Intent.ACTION_SCREEN_ON, deviceIdle = false, nowElapsedMs = 10_000L))
        assertFalse(gate.shouldWake(Intent.ACTION_SCREEN_ON, deviceIdle = false, nowElapsedMs = 30_000L))
        assertFalse(gate.shouldWake(Intent.ACTION_SCREEN_ON, deviceIdle = false, nowElapsedMs = 54_999L))
        assertTrue(gate.shouldWake(Intent.ACTION_SCREEN_ON, deviceIdle = false, nowElapsedMs = 55_000L))
    }

    @Test
    fun dozeExitAlwaysWakesEvenDuringScreenOnCooldown() {
        val gate = WakeMonitor.WakeGate(minScreenOnIntervalMs = 45_000L)

        assertTrue(gate.shouldWake(Intent.ACTION_SCREEN_ON, deviceIdle = false, nowElapsedMs = 10_000L))
        assertFalse(
            gate.shouldWake(
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED,
                deviceIdle = true,
                nowElapsedMs = 11_000L,
            ),
        )
        assertTrue(
            gate.shouldWake(
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED,
                deviceIdle = false,
                nowElapsedMs = 12_000L,
            ),
        )
        assertFalse(gate.shouldWake(Intent.ACTION_SCREEN_ON, deviceIdle = false, nowElapsedMs = 40_000L))
        assertTrue(gate.shouldWake(Intent.ACTION_SCREEN_ON, deviceIdle = false, nowElapsedMs = 57_000L))
    }

    @Test
    fun resetClearsDebounceWindow() {
        val gate = WakeMonitor.WakeGate(minScreenOnIntervalMs = 45_000L)

        assertTrue(gate.shouldWake(Intent.ACTION_SCREEN_ON, deviceIdle = false, nowElapsedMs = 10_000L))
        assertFalse(gate.shouldWake(Intent.ACTION_SCREEN_ON, deviceIdle = false, nowElapsedMs = 20_000L))
        gate.reset()
        assertTrue(gate.shouldWake(Intent.ACTION_SCREEN_ON, deviceIdle = false, nowElapsedMs = 25_000L))
    }
}
