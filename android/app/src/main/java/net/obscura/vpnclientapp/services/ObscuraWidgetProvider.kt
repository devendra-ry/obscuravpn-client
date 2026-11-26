package net.obscura.vpnclientapp.services

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import net.obscura.vpnclientapp.BuildConfig
import net.obscura.vpnclientapp.R

private const val ACTION_WIDGET_TOGGLE = "${BuildConfig.APPLICATION_ID}.actionWidgetToggle"

class ObscuraWidgetProvider : AppWidgetProvider() {
    internal data class WidgetPresentation(
        val statusText: String,
        val actionText: String,
        val isActive: Boolean,
    )

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_TOGGLE) {
            VpnServiceStateRegistry.toggleTunnelOrLaunch(context) { pending -> pending.send() }
            updateAll(context)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val runtime = VpnServiceStateRegistry.resolveRuntimeState(context)
        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, runtime))
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ObscuraWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val runtime = VpnServiceStateRegistry.resolveRuntimeState(context)
            val views = buildRemoteViews(context, runtime)
            for (appWidgetId in ids) {
                manager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun buildRemoteViews(context: Context, runtime: VpnServiceRuntimeState): RemoteViews {
            val presentation = presentWidget(context, runtime)
            return RemoteViews(context.packageName, R.layout.widget_obscura).apply {
                setTextViewText(R.id.widget_status, presentation.statusText)
                setTextViewText(R.id.widget_action_button, presentation.actionText)
                setInt(
                    R.id.widget_icon,
                    "setColorFilter",
                    ContextCompat.getColor(
                        context,
                        if (presentation.isActive) R.color.widget_accent else R.color.widget_subtitle,
                    ),
                )
                setInt(
                    R.id.widget_action_button,
                    "setBackgroundResource",
                    if (presentation.isActive) R.drawable.widget_btn_active else R.drawable.widget_btn_inactive,
                )
                setTextColor(
                    R.id.widget_action_button,
                    ContextCompat.getColor(
                        context,
                        if (presentation.isActive) R.color.white else R.color.widget_title,
                    ),
                )
                setOnClickPendingIntent(
                    R.id.widget_info_area,
                    VpnServiceStateRegistry.openMainAppPendingIntent(context),
                )
                setOnClickPendingIntent(R.id.widget_action_button, togglePendingIntent(context))
            }
        }

        private fun togglePendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, ObscuraWidgetProvider::class.java).apply { action = ACTION_WIDGET_TOGGLE },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        internal fun presentWidget(context: Context, runtime: VpnServiceRuntimeState): WidgetPresentation =
            when (runtime.connectionState) {
                TileConnectionState.Connected ->
                    WidgetPresentation(
                        statusText = context.getString(R.string.notification_vpn_status_connected),
                        actionText = context.getString(R.string.notification_action_disconnect),
                        isActive = true,
                    )
                TileConnectionState.Connecting ->
                    WidgetPresentation(
                        statusText = context.getString(R.string.notification_vpn_status_connecting),
                        actionText = context.getString(R.string.notification_action_disconnect),
                        isActive = true,
                    )
                TileConnectionState.Disconnected ->
                    WidgetPresentation(
                        statusText =
                            if (runtime.isLockdownEnabled) context.getString(R.string.qs_tile_lockdown)
                            else context.getString(R.string.notification_vpn_status_disconnected),
                        actionText = context.getString(R.string.widget_action_connect),
                        isActive = false,
                    )
            }
    }
}
