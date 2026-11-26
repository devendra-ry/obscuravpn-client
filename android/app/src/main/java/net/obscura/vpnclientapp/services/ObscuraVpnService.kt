package net.obscura.vpnclientapp.services

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.InetAddresses
import android.net.IpPrefix
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.system.OsConstants
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.Volatile
import net.obscura.lib.util.Logger
import net.obscura.vpnclientapp.BuildConfig
import net.obscura.vpnclientapp.client.ManagerCmd
import net.obscura.vpnclientapp.client.ManagerCmdOk
import net.obscura.vpnclientapp.client.RustFfi
import net.obscura.vpnclientapp.client.jsonConfig
import net.obscura.vpnclientapp.ui.JsonFfiBroadcastReceiver
import org.json.JSONArray
import org.json.JSONObject

private val logNoFfi = Logger(ObscuraVpnService::class)

private fun ApplicationExitInfo.toJson(): JSONObject =
    JSONObject()
        .put("processName", processName)
        .put("pid", pid)
        .put("reason", exitReasonLabel(reason) + "($reason)")
        .put("status", status)
        .put("importance", importance)
        .put("pssKb", pss)
        .put("rssKb", rss)
        .put("timestamp", timestamp)
        .put("description", description ?: JSONObject.NULL)

private fun exitReasonLabel(reason: Int): String =
    when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        else -> "UNKNOWN"
    }

const val PATH_REQUEST_VPN_START = "requestVpnStart"

private const val CONNECT_WAKELOCK_TIMEOUT_MS = 60_000L

@SuppressLint("VpnServicePolicy")
class ObscuraVpnService : VpnService() {
    private class Binder(
        val service: ObscuraVpnService,
    ) : IObscuraVpnService.Stub() {
        override fun stopTunnel() {
            service.log.info("stopTunnel", "Gf6f2lwW")
            service.stopTunnelFromUser()
        }

        override fun jsonFfi(id: Long, command: String) {
            val future = CompletableFuture<String>()
            service.rustFfi.jsonFfi(command, future)
            future.handle { value: String?, exception: Throwable? ->
                JsonFfiBroadcastReceiver.deliverInProcess(id, value, exception)
            }
        }

        override fun setAndroidSettings(json: String) {
            val next = jsonConfig.decodeFromString<AndroidVpnSettings>(json)
            service.updateAndroidSettings(next)
        }

        override fun getAndroidSettings(): String = jsonConfig.encodeToString(service.androidSettings)
    }

    companion object {
        private val instance = java.util.concurrent.atomic.AtomicReference<ObscuraVpnService?>(null)
        private val shutdownHookRegistered = java.util.concurrent.atomic.AtomicBoolean(false)
        private const val SESSION_NAME = "Obscura VPN"

        /** Whether the service is alive in this process, so its live status is the truth. */
        internal fun isRunning(): Boolean = instance.get() != null

        @androidx.annotation.Keep
        @JvmStatic
        fun ffiSetNetworkConfig(json: String): Int {
            val service = instance.get()
            if (service == null) {
                logNoFfi.error("ffiSetNetworkConfig called with no active service", "wK3xLm9p")
                return -1
            }
            val config: OsNetworkConfig =
                try {
                    jsonConfig.decodeFromString(json)
                } catch (e: Exception) {
                    service.log.error("failed to parse os network config: $e", "yN4zPn0q", e)
                    return -1
                }
            val pfd =
                try {
                    service.applyNetworkConfig(config)
                } catch (e: Exception) {
                    service.log.error("failed to apply os network config: $e", "U6hVQEJR", e)
                    return -1
                }
            return pfd?.detachFd() ?: -1
        }

        @androidx.annotation.Keep
        @JvmStatic
        fun ffiGetProcessExitReasons(): String? {
            val service = instance.get()
            if (service == null) {
                logNoFfi.error("ffiGetProcessExitReasons called with no active service", "Qb3mR7tx")
                return null
            }
            return try {
                val activityManager = service.getSystemService(ActivityManager::class.java)
                val array = JSONArray()
                for (info in activityManager.getHistoricalProcessExitReasons(service.packageName, 0, 0)) {
                    array.put(info.toJson())
                }
                array.toString()
            } catch (e: Throwable) {
                logNoFfi.error("ffiGetProcessExitReasons failed: $e", "V9nK4pLm", e)
                null
            }
        }

        @androidx.annotation.Keep
        @JvmStatic
        fun ffiStoreWgSecretKey(secretKey: ByteArray): Boolean {
            val service = instance.get() ?: return false
            return service.wgKeyVault.store(secretKey)
        }
    }

    private lateinit var rustFfi: RustFfi
    private lateinit var log: Logger
    private lateinit var notificationManager: NotificationManager
    private lateinit var handler: Handler
    private lateinit var wgKeyVault: WgKeyVault
    private lateinit var configKeyVault: ConfigKeyVault
    private lateinit var settingsStore: AndroidVpnSettingsStore
    private lateinit var wakeMonitor: WakeMonitor
    private lateinit var packageMonitor: PackageMonitor
    private lateinit var restoreStore: VpnRestoreStore
    private var connectingWakeLock: PowerManager.WakeLock? = null

    @Volatile private var destroyed = false
    @Volatile private var androidSettings: AndroidVpnSettings = AndroidVpnSettings()
    @Volatile private var vpnStatus: ManagerCmdOk.GetStatus.VpnStatus? = null
    @Volatile private var currentNetwork: Network? = null
    private var reportedNetwork: RustNetwork? = null

    private val connectivityManager
        get() = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun onCreate() {
        super.onCreate()
        destroyed = false
        logNoFfi.info("ObscuraVpnService onCreate entry")
        val vaultDir = noBackupFilesDir
        wgKeyVault = WgKeyVault(vaultDir)
        configKeyVault = ConfigKeyVault(vaultDir)
        settingsStore = AndroidVpnSettingsStore(vaultDir)
        restoreStore = VpnRestoreStore(this)
        androidSettings = settingsStore.load()
        connectingWakeLock =
            getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:connect")
                ?.apply { setReferenceCounted(false) }

        rustFfi =
            RustFfi(
                this,
                "obscura.net/android/${BuildConfig.VERSION_NAME}",
                wgKeyVault.load(),
                configKeyVault.loadOrCreate(),
                androidSettings.persistLogs,
            )
        log = rustFfi.logger(logNoFfi.tag)

        if (instance.getAndSet(this) != null) {
            log.error("instance already initialized", "xR4mNb7c")
        }

        log.info("onCreate alwaysOn=$isAlwaysOn lockdown=$isLockdownEnabled", "vqiGa01f")
        VpnServiceStateRegistry.updateSystemFlags(isAlwaysOn, isLockdownEnabled)
        VpnServiceStateRegistry.notifySystemSurfaces(this)
        logLastExitReason()
        registerShutdownHookIfNeeded()

        handler = Handler(Looper.getMainLooper())
        wakeMonitor = WakeMonitor(this) { rustFfi.wake() }
        packageMonitor = PackageMonitor(this, { androidSettings }) { refreshOsNetworkConfig() }
        packageMonitor.setEnabled(androidSettings.splitMode != SplitMode.Off)

        registerNetworkCallback()
        notificationManager = NotificationManager(this)
        loadStatus(null)
    }

    private fun registerShutdownHookIfNeeded() {
        if (!shutdownHookRegistered.compareAndSet(false, true)) return
        Runtime.getRuntime()
            .addShutdownHook(
                Thread {
                    val stacks =
                        Thread.getAllStackTraces().entries.joinToString("\n\n") { (thread, stack) ->
                            "${thread.name} (${thread.state})\n" + stack.joinToString("\n") { "    at $it" }
                        }
                    log.error(
                        "shutdown hook fired, System.exit in progress, all thread stacks:\n$stacks",
                        "aJx0R5wq",
                    )
                    rustFfi.flushAndStopPersistedLog()
                },
            )
    }

    private fun registerNetworkCallback() {
        val networkRequest =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
        connectivityManager.registerBestMatchingNetworkCallback(
            networkRequest,
            object : NetworkCallback() {
                override fun onAvailable(network: Network) {
                    currentNetwork = network
                    setUnderlyingNetworks(arrayOf(network))
                    connectivityManager.getLinkProperties(network)?.let { updateInterface(network, it) }
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    if (network == currentNetwork) {
                        updateInterface(network, linkProperties)
                    }
                }

                override fun onLost(network: Network) {
                    if (network == currentNetwork) {
                        currentNetwork = null
                        setUnderlyingNetworks(emptyArray())
                        updateInterface(null, null)
                    }
                }
            },
            handler,
        )
        reportDefaultNetwork()
    }

    private fun reportDefaultNetwork() {
        val network = connectivityManager.activeNetwork ?: return
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return
        val linkProps = connectivityManager.getLinkProperties(network) ?: return
        val usable =
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        if (!usable) return
        currentNetwork = network
        setUnderlyingNetworks(arrayOf(network))
        updateInterface(network, linkProps)
    }

    private fun logLastExitReason() {
        try {
            val currentProcess = Application.getProcessName()
            val info =
                getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessExitReasons(packageName, 0, 0)
                    .filter { it.processName == currentProcess }
                    .maxByOrNull { it.timestamp }
            log.info("last exit of this process: ${info?.toJson() ?: "none recorded"}", "sV2mHd7K")
        } catch (e: Throwable) {
            log.error("failed to query last exit reason: $e", "jW5pQn3F", e)
        }
    }

    private fun promoteToForeground(prepareResult: PrepareResult): Result<Unit> =
        runCatching {
                if (prepareResult == PrepareResult.Ready) {
                    this.startForeground(
                        NotificationManager.NOTIFICATION_ID,
                        this.notificationManager.buildNotification(this.vpnStatus, prepareResult),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
                    )
                } else {
                    throw RuntimeException("VPN not prepared")
                }
            }
            .onFailure {
                log.error(
                    "failed to promote service to foreground: ${it.message}",
                    "GKs2Deov",
                )
            }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log.info("onStartCommand $intent ${intent?.action} $flags $startId", "C9rsG0uh")
        val prepareResult = this.prepareVpnService()
        val isForeground = this.promoteToForeground(prepareResult).isSuccess
        when (intent?.action) {
            ACTION_START_TUNNEL -> startWithArgs(intent.getStartTunnelExtras(), isForeground)
            ACTION_STOP_TUNNEL -> {
                stopTunnelFromUser()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            SERVICE_INTERFACE -> {
                log.info(
                    "onStartCommand was system-initiated alwaysOn=$isAlwaysOn lockdown=$isLockdownEnabled",
                    "sktWFegO",
                )
                VpnServiceStateRegistry.updateSystemFlags(isAlwaysOn, isLockdownEnabled)
                startWithArgs(restoreStore.lastTunnelArgs, isForeground)
            }
            null -> {
                log.info("onStartCommand restored after process kill", "pR7mKx2q")
                if (isForeground && restoreStore.wantConnected) {
                    startTunnel(restoreStore.lastTunnelArgs)
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
            }
        }
        if (!isForeground) {
            this.notificationManager.notify(this.vpnStatus, prepareResult)
            this.stopSelfResult(startId)
        }
        return START_STICKY
    }

    private fun startWithArgs(args: String?, isForeground: Boolean) {
        // Without the foreground promotion (no VPN permission) nothing starts, so nothing is remembered as wanted.
        if (!isForeground) return
        restoreStore.setWantConnected(true, args)
        startTunnel(args)
    }

    override fun onBind(intent: Intent?): IBinder {
        log.info("onBind $intent ${intent?.action}", "lckBR8hX")
        if (intent?.action == SERVICE_INTERFACE) {
            log.info("onBind was system-initiated", "4olaayXf")
            this.promoteToForeground(PrepareResult.Ready)
        }
        return super.onBind(intent) ?: Binder(this)
    }

    override fun onRebind(intent: Intent?) {
        log.info("onRebind $intent ${intent?.action}", "AcVtL2Ub")
        super.onRebind(intent)
        if (intent?.action == SERVICE_INTERFACE) {
            log.info("onRebind was system-initiated", "YsdxJ7Ni")
            this.promoteToForeground(PrepareResult.Ready)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        log.info("onUnbind $intent ${intent?.action}", "woAdA7g2")
        if (intent?.action == SERVICE_INTERFACE) {
            log.info("onUnbind was system-initiated", "oNOWQoPR")
            this.stopForeground(STOP_FOREGROUND_DETACH)
        }
        return true
    }

    private fun onStatusUpdated(status: ManagerCmdOk.GetStatus) {
        if (destroyed) return
        log.info("status updated $status", "xXx7PxdD")
        this.vpnStatus = status.vpnStatus
        val loggedIn = status.accountId != null && !status.inNewAccountFlow
        val loggedInChanged = restoreStore.setLoggedIn(loggedIn)
        restoreStore.setAutoConnect(status.autoConnect && loggedIn)
        if (!loggedIn) {
            restoreStore.setWantConnected(false, null)
        }
        val prevState = VpnServiceStateRegistry.state.value
        VpnServiceStateRegistry.updateFromStatus(status.vpnStatus, isAlwaysOn, isLockdownEnabled)
        // The widget's button depends on the login state too, so a login or logout redraws it.
        if (loggedInChanged || prevState != VpnServiceStateRegistry.state.value) {
            VpnServiceStateRegistry.notifySystemSurfaces(this)
        }
        val isConnecting = status.vpnStatus is ManagerCmdOk.GetStatus.VpnStatus.Connecting
        updateConnectWakeLock(isConnecting)
        val active = status.vpnStatus !is ManagerCmdOk.GetStatus.VpnStatus.Disconnected
        handler.post { wakeMonitor.setEnabled(active) }
        this.notificationManager.notify(
            status.vpnStatus,
            if (!active) this.prepareVpnService() else PrepareResult.Ready,
        )
        this.loadStatus(status.version)
    }

    private fun updateConnectWakeLock(connecting: Boolean) {
        val wl = connectingWakeLock ?: return
        if (connecting) {
            if (!wl.isHeld) wl.acquire(CONNECT_WAKELOCK_TIMEOUT_MS)
        } else if (wl.isHeld) {
            wl.release()
        }
    }

    override fun onRevoke() {
        log.info("onRevoke", "V3qS5kil")
        stopTunnelFromUser()
        VpnServiceStateRegistry.markDisconnected(isAlwaysOn, isLockdownEnabled)
        VpnServiceStateRegistry.notifySystemSurfaces(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onRevoke()
    }

    override fun onDestroy() {
        destroyed = true
        updateConnectWakeLock(false)
        super.onDestroy()
        if (instance.getAndSet(null) == null) {
            log.error("instance already cleared", "bQ5wKr8d")
        }
        wakeMonitor.setEnabled(false)
        packageMonitor.setEnabled(false)
        VpnServiceStateRegistry.markDisconnected(isAlwaysOn, isLockdownEnabled)
        VpnServiceStateRegistry.notifySystemSurfaces(this)
        log.info("onDestroy", "yNLRpqaN")
        stopTunnel()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::log.isInitialized) {
            log.info("onTrimMemory level=$level", "mT9rKp4w")
        }
    }

    private fun loadStatus(knownVersion: String?) {
        if (destroyed) return
        log.info("load status $knownVersion", "8pXipD8h")
        CompletableFuture<String>().also {
            rustFfi.jsonFfi(
                jsonConfig.encodeToString(ManagerCmd.GetStatus(knownVersion)),
                it,
            )
            it.handle { data, tr ->
                log.info("getStatus completed $data", "oiAyY4gh", tr)
                data?.let { payload -> onStatusUpdated(jsonConfig.decodeFromString(payload)) }
            }
        }
    }

    private fun updateAndroidSettings(next: AndroidVpnSettings) {
        val old = androidSettings
        androidSettings = next
        settingsStore.save(next)
        handler.post { packageMonitor.setEnabled(next.splitMode != SplitMode.Off) }
        val tunChanged =
            old.customDns != next.customDns ||
                old.splitMode != next.splitMode ||
                old.splitApps != next.splitApps ||
                old.excludedCidrs != next.excludedCidrs
        if (tunChanged) {
            refreshOsNetworkConfig()
        }
    }

    private fun refreshOsNetworkConfig() {
        CompletableFuture<String>().also { rustFfi.jsonFfi("""{"refreshOsNetworkConfig":{}}""", it) }
    }

    private fun setTunnelArgs(exit: String?, active: Boolean?) {
        CompletableFuture<String>().also {
            rustFfi.jsonFfi(
                jsonConfig.encodeToString(
                    ManagerCmd.SetTunnelArgs(
                        args = exit?.let { raw -> jsonConfig.decodeFromString(raw) },
                        active = active,
                    ),
                ),
                it,
            )
        }
    }

    private fun stopTunnelFromUser() {
        restoreStore.setWantConnected(false, null)
        stopTunnel()
    }

    private fun stopTunnel() {
        setTunnelArgs(null, false)
    }

    private fun startTunnel(exitSelector: String?) {
        setTunnelArgs(exitSelector, true)
    }

    private fun applyNetworkConfig(networkConfig: OsNetworkConfig): ParcelFileDescriptor? {
        log.info("applying network config", "q9cnmRY0")
        val plan =
            TunPlan.of(
                config = networkConfig,
                settings = androidSettings,
                ownPackage = packageName,
                isUsableDns = ::isUsableDns,
                isInstalled = ::isInstalled,
            )
        val pfd = buildVpnInterface(plan).establish()
        if (pfd == null) {
            log.error("VpnService.Builder.establish() returned null", "tR7uWe2x")
        }
        return pfd
    }

    private fun buildVpnInterface(plan: TunPlan): Builder =
        Builder().apply {
            setSession(SESSION_NAME)
            setMtu(plan.mtu)
            setMetered(false)
            setBlocking(false)
            plan.addresses.forEach { addAddress(it.address, it.prefix) }
            plan.routes.forEach { addRoute(it.address, it.prefix) }
            plan.excludedRoutes.forEach {
                excludeRoute(IpPrefix(InetAddresses.parseNumericAddress(it.address), it.prefix))
            }
            plan.dnsServers.forEach { addDnsServer(it) }
            currentNetwork?.let { setUnderlyingNetworks(arrayOf(it)) }
            allowFamily(OsConstants.AF_INET)
            allowFamily(OsConstants.AF_INET6)
            when (val apps = plan.apps) {
                is AppScope.AllExcept -> apps.packages.forEach { addDisallowedApplication(it) }
                is AppScope.Only -> apps.packages.forEach { allowApp(this, it) }
            }
        }

    private fun allowApp(builder: Builder, packageName: String) {
        try {
            builder.addAllowedApplication(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            log.warn("allowed app skipped: $packageName")
        }
    }

    private fun isUsableDns(value: String): Boolean =
        InetAddresses.isNumericAddress(value) &&
            InetAddresses.parseNumericAddress(value).let { !it.isLoopbackAddress && !it.isAnyLocalAddress }

    private fun isInstalled(packageName: String): Boolean =
        try {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private fun updateInterface(network: Network?, linkProperties: LinkProperties?) {
        val next =
            if (network != null && linkProperties != null) {
                readRustNetwork(network.networkHandle, linkProperties)
            } else {
                null
            }
        if (next == reportedNetwork) return
        reportedNetwork = next
        log.info("network interface changed: $next", "crWriIOe")
        if (next != null) {
            rustFfi.setNetworkInterface(next.name, next.index, next.networkHandle, next.mtuHint)
        } else {
            rustFfi.unsetNetworkInterface()
        }
    }
}
