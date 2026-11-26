package net.obscura.vpnclientapp.services

import android.content.Context
import androidx.core.content.edit

internal class VpnRestoreStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val wantConnected: Boolean
        get() = prefs.getBoolean(KEY_WANT_CONNECTED, false)

    val lastTunnelArgs: String?
        get() = prefs.getString(KEY_LAST_ARGS, null)

    val autoConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONNECT, false)

    val loggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false)

    fun setWantConnected(want: Boolean, tunnelArgsJson: String?) {
        prefs.edit {
            putBoolean(KEY_WANT_CONNECTED, want)
            if (tunnelArgsJson != null) {
                putString(KEY_LAST_ARGS, tunnelArgsJson)
            } else if (!want) {
                remove(KEY_LAST_ARGS)
            }
        }
    }

    fun setAutoConnect(enabled: Boolean) {
        if (autoConnect != enabled) {
            prefs.edit { putBoolean(KEY_AUTO_CONNECT, enabled) }
        }
    }

    fun setLoggedIn(isLoggedIn: Boolean) {
        if (loggedIn != isLoggedIn) {
            prefs.edit { putBoolean(KEY_LOGGED_IN, isLoggedIn) }
        }
    }

    fun shouldStartOnBoot(): Boolean = autoConnect

    fun shouldStartOnUpgrade(): Boolean = autoConnect || wantConnected

    private companion object {
        const val PREFS_NAME = "vpn_restore_prefs"
        const val KEY_WANT_CONNECTED = "want_connected"
        const val KEY_LAST_ARGS = "last_tunnel_args"
        const val KEY_AUTO_CONNECT = "auto_connect"
        const val KEY_LOGGED_IN = "logged_in"
    }
}
