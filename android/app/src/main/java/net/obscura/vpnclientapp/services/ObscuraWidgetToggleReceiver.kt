package net.obscura.vpnclientapp.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import net.obscura.lib.util.Logger

private val log = Logger(ObscuraWidgetToggleReceiver::class)

/**
 * Handles the widget's connect/disconnect button. Declared with `android:exported="false"`: only this app's own
 * PendingIntent reaches it, unlike [ObscuraWidgetProvider], which must be exported for the launcher's update
 * broadcasts.
 */
class ObscuraWidgetToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WIDGET_TOGGLE) return
        VpnServiceStateRegistry.toggleTunnelOrLaunch(context) { pending ->
            // The widget normally opens the app itself in this case. This only runs if the state changed after the
            // widget was drawn; Android may then block the start from the background, so redraw the widget either way.
            runCatching { pending.send() }.onFailure { log.warn("cannot open the app from the widget: ${it.message}") }
        }
        ObscuraWidgetProvider.updateAll(context)
    }
}
