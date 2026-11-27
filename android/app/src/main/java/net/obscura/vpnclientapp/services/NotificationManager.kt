package net.obscura.vpnclientapp.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager as PlatformNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import net.obscura.lib.util.Logger
import net.obscura.vpnclientapp.BuildConfig
import net.obscura.vpnclientapp.R
import net.obscura.vpnclientapp.activities.MainActivity
import net.obscura.vpnclientapp.client.ManagerCmdOk
import net.obscura.vpnclientapp.ui.OsStatus

private val log = Logger(NotificationManager::class)

internal class NotificationManager(private val context: Context) {
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "vpn_channel"
        private const val ALERT_CHANNEL_ID = "vpn_alerts"
        const val NOTIFICATION_ID = 1
        private const val ALERT_ID = 2
        private const val PENDING_INTENT_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }

    private val manager = NotificationManagerCompat.from(this.context)

    init {
        this.context
            .getSystemService(PlatformNotificationManager::class.java)
            ?.createNotificationChannel(
                NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        this.context.getString(R.string.notification_channel_vpn_name),
                        PlatformNotificationManager.IMPORTANCE_LOW,
                    )
                    .apply {
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        enableLights(false)
                        enableVibration(false)
                        setSound(null, null)
                    },
            )
        this.context
            .getSystemService(PlatformNotificationManager::class.java)
            ?.createNotificationChannel(
                NotificationChannel(
                    ALERT_CHANNEL_ID,
                    this.context.getString(R.string.notification_channel_alerts_name),
                    PlatformNotificationManager.IMPORTANCE_HIGH,
                ),
            )
    }

    private fun buildIntentMainActivity(path: String?) =
        PendingIntent.getActivity(
            this.context,
            0,
            Intent().apply {
                this.action = Intent.ACTION_MAIN
                path?.let { this.data = "/$it".toUri() }
                this.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                this.setClassName(
                    BuildConfig.APPLICATION_ID,
                    MainActivity::class.qualifiedName!!,
                )
            },
            PENDING_INTENT_FLAGS,
        )

    private fun buildIntentVpnService(action: String) =
        // `getForegroundService` would be functionally equivalent, but leads to an ANR if the action is stale and VPN
        // permission was revoked. While this initially starts the service in the background, `VpnService` is allowed
        // to be started in the background on a temporary basis:
        // https://developer.android.com/about/versions/oreo/background#services
        PendingIntent.getService(
            this.context,
            0,
            Intent().apply {
                this.action = action
                this.setClassName(
                    BuildConfig.APPLICATION_ID,
                    ObscuraVpnService::class.qualifiedName!!,
                )
            },
            PENDING_INTENT_FLAGS,
        )

    private fun buildAction(resId: Int, pendingIntent: PendingIntent) =
        NotificationCompat.Action.Builder(
                null, // The icon argument has been ignored since Android 26.
                this.context.getString(resId),
                pendingIntent,
            )
            .build()

    private fun buildConnectionAction(vpnStatus: ManagerCmdOk.GetStatus.VpnStatus?, prepareResult: PrepareResult) =
        when (vpnStatus) {
            is ManagerCmdOk.GetStatus.VpnStatus.Connected,
            is ManagerCmdOk.GetStatus.VpnStatus.Connecting,
            ->
                this.buildAction(
                    R.string.notification_action_disconnect,
                    this.buildIntentVpnService(ACTION_STOP_TUNNEL),
                )
            is ManagerCmdOk.GetStatus.VpnStatus.Disconnected,
            null ->
                this.buildAction(
                    R.string.notification_action_quick_connect,
                    when (prepareResult) {
                        PrepareResult.Ready -> this.buildIntentVpnService(ACTION_START_TUNNEL)
                        else -> this.buildIntentMainActivity(PATH_REQUEST_VPN_START)
                    },
                )
        }

    private fun buildLocationAction() =
        this.buildAction(
            R.string.notification_action_location,
            this.buildIntentMainActivity(OsStatus.NavigationView.Location.serialName()),
        )

    fun buildNotification(vpnStatus: ManagerCmdOk.GetStatus.VpnStatus?, prepareResult: PrepareResult) =
        NotificationCompat.Builder(this.context, NOTIFICATION_CHANNEL_ID)
            .addAction(this.buildConnectionAction(vpnStatus, prepareResult))
            .addAction(this.buildLocationAction())
            .setContentIntent(this.buildIntentMainActivity(null))
            .setContentTitle(this.context.getString(R.string.app_name))
            .setContentText(
                this.context.getString(
                    R.string.notification_vpn_text,
                    when (vpnStatus) {
                        is ManagerCmdOk.GetStatus.VpnStatus.Connected ->
                            this.context.getString(R.string.notification_vpn_status_connected)
                        is ManagerCmdOk.GetStatus.VpnStatus.Connecting ->
                            this.context.getString(R.string.notification_vpn_status_connecting)
                        is ManagerCmdOk.GetStatus.VpnStatus.Disconnected,
                        null -> this.context.getString(R.string.notification_vpn_status_disconnected)
                    },
                ),
            )
            .setSmallIcon(R.drawable.ic_stat_name)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setLocalOnly(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    /** Warns that the tunnel is down: [stopped] when it stopped for good, otherwise while the core reconnects. */
    fun showConnectionLost(stopped: Boolean, prepareResult: PrepareResult) = runCatching {
        if (!this.canPost()) return@runCatching
        val builder =
            NotificationCompat.Builder(this.context, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(
                    this.context.getString(
                        if (stopped) R.string.notification_disconnected_title else R.string.notification_lost_title,
                    ),
                )
                .setContentText(
                    this.context.getString(
                        if (stopped) R.string.notification_disconnected_text else R.string.notification_lost_text,
                    ),
                )
                .setContentIntent(this.buildIntentMainActivity(null))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setLocalOnly(true)
        if (stopped) {
            builder.addAction(
                this.buildAction(
                    R.string.notification_action_reconnect,
                    when (prepareResult) {
                        PrepareResult.Ready -> this.buildIntentVpnService(ACTION_START_TUNNEL)
                        else -> this.buildIntentMainActivity(PATH_REQUEST_VPN_START)
                    },
                ),
            )
        }
        this.manager.notify(ALERT_ID, builder.build())
    }
        .onFailure { log.error("failed to post the connection alert: ${it.message}") }

    fun clearConnectionLost() = runCatching { this.manager.cancel(ALERT_ID) }

    private fun canPost() =
        ContextCompat.checkSelfPermission(this.context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notify(vpnStatus: ManagerCmdOk.GetStatus.VpnStatus?, prepareResult: PrepareResult) = runCatching {
        // Defensive permission check in case user revokes permissions.
        if (
            ContextCompat.checkSelfPermission(
                this.context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            this.manager.notify(
                NOTIFICATION_ID,
                this.buildNotification(vpnStatus, prepareResult),
            )
        }
    }
        .onFailure { log.error("failed to update notification: ${it.message}") }
}
