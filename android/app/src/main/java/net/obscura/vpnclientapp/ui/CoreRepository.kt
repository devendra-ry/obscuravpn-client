package net.obscura.vpnclientapp.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import net.obscura.lib.util.Logger
import net.obscura.vpnclientapp.activities.MainActivity
import net.obscura.vpnclientapp.client.AccountIdUtils
import net.obscura.vpnclientapp.client.CachedExitList
import net.obscura.vpnclientapp.client.CachedList
import net.obscura.vpnclientapp.client.CoreStatus
import net.obscura.vpnclientapp.client.CoreVpnStatus
import net.obscura.vpnclientapp.client.DnsContentBlock
import net.obscura.vpnclientapp.client.ErrorCodeException
import net.obscura.vpnclientapp.client.ExitSelector
import net.obscura.vpnclientapp.client.ManagerCmd
import net.obscura.vpnclientapp.client.ManagerCmdOk
import net.obscura.vpnclientapp.client.OneExit
import net.obscura.vpnclientapp.client.OneRelay
import net.obscura.vpnclientapp.client.PinnedLocation
import net.obscura.vpnclientapp.client.RelaySelector
import net.obscura.vpnclientapp.client.ServiceStatusOutput
import net.obscura.vpnclientapp.client.TrafficStats
import net.obscura.vpnclientapp.client.TunnelArgs
import net.obscura.vpnclientapp.client.TunnelMode
import net.obscura.vpnclientapp.client.jsonConfig
import net.obscura.vpnclientapp.services.AndroidVpnSettings
import net.obscura.vpnclientapp.services.IObscuraVpnService
import net.obscura.vpnclientapp.services.connectionLost
import net.obscura.vpnclientapp.ui.compose.modeLabel

private val log = Logger(CoreRepository::class)

sealed interface ListLoadState<out T> {
    data object Loading : ListLoadState<Nothing>

    data class Ready<T>(val items: List<T>) : ListLoadState<T>

    data class Error(val code: String) : ListLoadState<Nothing>
}

class CoreRepository(
    private val binder: IObscuraVpnService,
    private val activity: MainActivity,
    private val osStatusManager: OsStatusManager,
    private val isFreshLaunch: Boolean,
) {
    private val _status = MutableStateFlow<CoreStatus?>(null)
    val status: StateFlow<CoreStatus?> = _status.asStateFlow()

    private val _exits = MutableStateFlow<ListLoadState<OneExit>>(ListLoadState.Loading)
    val exits: StateFlow<ListLoadState<OneExit>> = _exits.asStateFlow()

    private val _relays = MutableStateFlow<ListLoadState<OneRelay>>(ListLoadState.Loading)
    val relays: StateFlow<ListLoadState<OneRelay>> = _relays.asStateFlow()

    private val _trafficStats = MutableStateFlow<TrafficStats?>(null)
    val trafficStats: StateFlow<TrafficStats?> = _trafficStats.asStateFlow()

    private val _androidSettings = MutableStateFlow(AndroidVpnSettings())
    val androidSettings: StateFlow<AndroidVpnSettings> = _androidSettings.asStateFlow()

    private val _serviceStatus = MutableStateFlow<ServiceStatusOutput?>(null)
    val serviceStatus: StateFlow<ServiceStatusOutput?> = _serviceStatus.asStateFlow()

    private val _protocolNotice = MutableStateFlow<String?>(null)

    /** Set when the protocol was switched after a connection failed; the VPN tab shows it until dismissed. */
    val protocolNotice: StateFlow<String?> = _protocolNotice.asStateFlow()

    /** Whether a failed connection moves to the next protocol by itself (Settings › Protocol). */
    @Volatile var autoSwitchProtocol: Boolean = true

    private val _connectionLost = MutableStateFlow(false)

    /** The tunnel was up and is now down while the core brings it back: the VPN tab turns red. */
    val connectionLost: StateFlow<Boolean> = _connectionLost.asStateFlow()
    private var wasConnected = false

    /** Protocols already tried in the current connection attempt, so a fallback never loops. */
    private val fallbackTried = HashSet<TunnelMode>()

    private val jobs = ArrayList<Job>()
    private var autoConnectEligible = isFreshLaunch

    fun start(owner: LifecycleOwner) {
        val scope = owner.lifecycleScope
        loadAndroidSettings()
        jobs += launchStatusLoop(owner, scope)
        jobs += launchExitListLoop(owner, scope)
        jobs += launchRelayListLoop(owner, scope)
        jobs += launchStatsLoop(owner, scope)
        jobs += launchAccountLoop(owner, scope)
        jobs += launchPurchaseUploader(owner, scope)
        jobs += scope.launch { refreshServiceStatus() }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    private fun loadAndroidSettings() {
        runCatching { jsonConfig.decodeFromString<AndroidVpnSettings>(binder.getAndroidSettings()) }
            .onSuccess { _androidSettings.value = it }
            .onFailure { log.warn("failed to read android settings: ${it.message}") }
    }

    private fun launchStatusLoop(owner: LifecycleOwner, scope: CoroutineScope): Job = scope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var knownVersion: String? = null
            while (isActive) {
                runCatching {
                    val raw =
                        invokeFfi(
                            """{"getStatus":{"knownVersion":${knownVersion?.let { "\"$it\"" } ?: "null"}}}""",
                            null,
                        )
                    val next = jsonConfig.decodeFromString<CoreStatus>(raw)
                    knownVersion = next.version
                    _status.value = next
                    publishOsVpnStatus(next.vpnStatus)
                    maybeAutoConnect(next)
                    trackConnectionLost(next.vpnStatus)
                    maybeSwitchProtocol(next)
                }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        log.error("status loop error: ${e.message}")
                        delay(500.milliseconds)
                    }
                delay(10.milliseconds)
            }
        }
    }

    private fun launchExitListLoop(owner: LifecycleOwner, scope: CoroutineScope): Job = scope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var knownVersion: String? = null
            while (isActive) {
                runCatching {
                    val raw =
                        invokeFfi(
                            """{"getExitList":{"knownVersion":${knownVersion?.let { "\"$it\"" } ?: "null"}}}""",
                            null,
                        )
                    val list = jsonConfig.decodeFromString<CachedExitList>(raw)
                    knownVersion = list.version
                    _exits.value = ListLoadState.Ready(list.value.exits)
                }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        if (_exits.value is ListLoadState.Loading) {
                            log.warn("failed to load the exit list: ${e.message}", "Ex4mLd7q")
                            _exits.value = ListLoadState.Error((e as? ErrorCodeException)?.errorCode ?: "apiError")
                        }
                        delay(3.seconds)
                    }
                delay(50.milliseconds)
            }
        }
    }

    private fun launchRelayListLoop(owner: LifecycleOwner, scope: CoroutineScope): Job = scope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var knownVersion: String? = null
            while (isActive) {
                runCatching {
                    val ver = knownVersion?.let { "\"$it\"" } ?: "null"
                    val raw = invokeFfi("""{"getRelayList":{"knownVersion":$ver}}""", null)
                    val list = jsonConfig.decodeFromString<CachedList<OneRelay>>(raw)
                    knownVersion = list.version
                    _relays.value = ListLoadState.Ready(list.value)
                }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        if (_relays.value is ListLoadState.Loading) {
                            log.warn("failed to load the relay list: ${e.message}", "Rl5yLd2w")
                            _relays.value = ListLoadState.Error((e as? ErrorCodeException)?.errorCode ?: "apiError")
                        }
                        delay(3.seconds)
                    }
                delay(50.milliseconds)
            }
        }
    }

    private fun launchStatsLoop(owner: LifecycleOwner, scope: CoroutineScope): Job = scope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                if (_status.value?.vpnStatus?.connected != null) {
                    runCatching {
                        val raw = invokeFfi("""{"getTrafficStats":{}}""", 5_000L)
                        _trafficStats.value = jsonConfig.decodeFromString<TrafficStats>(raw)
                    }
                        .onFailure { e ->
                            if (e is CancellationException) throw e
                            log.warn("failed to read traffic stats: ${e.message}", "Ts3kRd8v")
                        }
                } else {
                    _trafficStats.value = null
                }
                delay(1.seconds)
            }
        }
    }

    private fun launchPurchaseUploader(owner: LifecycleOwner, scope: CoroutineScope): Job = scope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val purchaseTokens = activity.billingFacade.fetchPurchaseTokens() ?: return@repeatOnLifecycle
            for (token in purchaseTokens) {
                runCatching { uploadPurchaseToken(binder, token, null) }
            }
        }
    }

    private fun publishOsVpnStatus(vpnStatus: CoreVpnStatus) {
        val osState =
            when {
                vpnStatus.connected != null -> OsStatus.OsVpnStatus.Connected
                vpnStatus.connecting != null -> OsStatus.OsVpnStatus.Connecting
                else -> OsStatus.OsVpnStatus.Disconnected
            }
        osStatusManager.update { this.vpnStatus = osState }
    }

    private suspend fun maybeAutoConnect(status: CoreStatus) {
        val shouldAutoConnect =
            autoConnectEligible &&
                status.autoConnect &&
                status.accountId != null &&
                !status.inNewAccountFlow &&
                status.vpnStatus.disconnected != null
        autoConnectEligible = false
        if (shouldAutoConnect) {
            activity.vpnPermissionRequestManager.requestVpnStart(null)
        }
    }

    private suspend fun maybeSwitchProtocol(status: CoreStatus) {
        val connecting = status.vpnStatus.connecting
        if (connecting == null) {
            fallbackTried.clear()
            return
        }
        val code = connecting.connectError ?: return
        val mode = connecting.tunnelArgs.mode
        fallbackTried += mode
        if (!autoSwitchProtocol) return
        val anyRelay = connecting.tunnelArgs.relay == RelaySelector.Any
        val fallback = nextFallback(code, mode, anyRelay, fallbackTried) ?: return
        log.info("falling back after $code in $mode: $fallback", "Pf6bSw2q")
        val (args, notice) =
            when (fallback) {
                is Fallback.Protocol -> {
                    fallbackTried += fallback.mode
                    status.tunnelArgs.copy(mode = fallback.mode) to
                        "${modeLabel(mode)} could not connect on this network, so Obscura switched to " +
                            "${modeLabel(fallback.mode)}."
                }
                Fallback.FastestRelay ->
                    status.tunnelArgs.copy(relay = RelaySelector.Any) to
                        "Your chosen entry relay could not be reached, so Obscura is using the fastest relay."
            }
        updateTunnelArgs(args).onSuccess { _protocolNotice.value = notice }
    }

    private fun trackConnectionLost(vpn: CoreVpnStatus) {
        val connecting = vpn.connecting
        _connectionLost.value =
            connecting != null && connectionLost(wasConnected, connecting.reconnecting, connecting.connectError)
        when {
            vpn.connected != null -> wasConnected = true
            vpn.disconnected != null -> wasConnected = false
        }
    }

    fun dismissProtocolNotice() {
        _protocolNotice.value = null
    }

    suspend fun invokeFfi(cmdJson: String, timeoutMs: Long? = 15_000L): String {
        val deferred = JsonFfiResults.waitForResponse(binder, cmdJson)
        return if (timeoutMs != null) {
            withTimeout(timeoutMs) { deferred.await() }
        } else {
            deferred.await()
        }
    }

    suspend fun connect(exit: ExitSelector? = null, relay: RelaySelector? = null): Result<Unit> {
        val stored = _status.value?.tunnelArgs ?: TunnelArgs()
        val target = stored.copy(exit = exit ?: stored.exit, relay = relay ?: stored.relay)
        val argsJson = jsonConfig.encodeToString(target)
        return activity.vpnPermissionRequestManager.requestVpnStart(argsJson)
    }

    fun disconnect() {
        runCatching { binder.stopTunnel() }
    }

    /**
     * Stores [args]; Rust reconnects at once when the tunnel is up. Callers fire this from `scope.launch` in the UI,
     * where an uncaught exception would crash the app, so a failure is logged and returned instead of thrown.
     */
    suspend fun updateTunnelArgs(args: TunnelArgs): Result<Unit> =
        try {
            val json =
                jsonConfig.encodeToString(
                    ManagerCmd.SetTunnelArgs(
                        args = jsonConfig.decodeFromString(jsonConfig.encodeToString(args)),
                        active = null,
                    ),
                )
            invokeFfi(json)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error("failed to update tunnel args: ${e.message}", tr = e)
            Result.failure(e)
        }

    suspend fun setWgKeyMaxAge(secs: Long) {
        invokeFfi("""{"setWgKeyMaxAge":{"secs":$secs}}""")
    }

    suspend fun setAutoConnect(enable: Boolean) {
        invokeFfi("""{"setAutoConnect":{"enable":$enable}}""")
    }

    suspend fun setDnsContentBlock(block: DnsContentBlock) {
        invokeFfi("""{"setDnsContentBlock":{"value":${jsonConfig.encodeToString(block)}}}""")
    }

    suspend fun setUseSystemDns(enable: Boolean) {
        invokeFfi("""{"setUseSystemDns":{"enable":$enable}}""")
    }

    suspend fun setLocalNetworkAccess(enable: Boolean) {
        invokeFfi("""{"setLocalNetworkAccess":{"enable":$enable}}""")
    }

    suspend fun setFeatureFlag(flag: String, active: Boolean) {
        invokeFfi("""{"setFeatureFlag":{"flag":${jsonConfig.encodeToString(flag)},"active":$active}}""")
    }

    suspend fun setPinnedExits(exits: List<PinnedLocation>) {
        invokeFfi("""{"setPinnedExits":{"exits":${jsonConfig.encodeToString(exits)}}}""")
    }

    suspend fun setSniRelay(host: String?) {
        invokeFfi("""{"setSniRelay":{"host":${jsonConfig.encodeToString(host)}}}""")
    }

    suspend fun setApiHostAlternate(host: String?) {
        invokeFfi("""{"setApiHostAlternate":{"host":${jsonConfig.encodeToString(host)}}}""")
    }

    suspend fun rotateWgKey() {
        invokeFfi("""{"rotateWgKey":{}}""")
    }

    suspend fun refreshExits() {
        invokeFfi("""{"refreshExitList":{"freshness":0}}""", 30_000L)
    }

    suspend fun refreshRelays() {
        invokeFfi("""{"refreshRelayList":{"freshness":0}}""", 30_000L)
    }

    suspend fun login(accountId: String, validate: Boolean = true) {
        invokeFfi("""{"setInNewAccountFlow":{"value":false}}""")
        invokeFfi("""{"login":{"accountId":"$accountId","validate":$validate}}""", 65_000L)
    }

    suspend fun createAccount(): String {
        val newId = AccountIdUtils.generateAccountNumber()
        invokeFfi("""{"setInNewAccountFlow":{"value":true}}""")
        invokeFfi("""{"login":{"accountId":"$newId","validate":true}}""", 65_000L)
        return newId
    }

    suspend fun finishNewAccountFlow() {
        invokeFfi("""{"setInNewAccountFlow":{"value":false}}""")
    }

    suspend fun logout() {
        disconnect()
        invokeFfi("""{"logout":{}}""")
    }

    suspend fun deleteAccount() {
        invokeFfi("""{"apiDeleteAccount":{}}""", 30_000L)
        logout()
    }

    /** Rust stores the answer in the status (`account`), which is what the screens read. */
    suspend fun refreshAccountInfo(): Boolean =
        try {
            invokeFfi("""{"apiGetAccountInfo":{}}""", 30_000L)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("failed to load account info: ${e.message}", "Ac7nTf3k")
            false
        }

    /**
     * Rust only fetches the account when asked, so without this `account` stays empty after login ("Validating
     * Account..." for ever). Like the desktop UI: at once when logged in, then every 12 hours, every minute while a new
     * account waits for its first payment, and 30 seconds after a failure.
     */
    private fun launchAccountLoop(owner: LifecycleOwner, scope: CoroutineScope): Job = scope.launch {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            _status
                .map { s -> if (s?.accountId == null) null else s.inNewAccountFlow }
                .distinctUntilChanged()
                .collectLatest { newAccountFlow ->
                    if (newAccountFlow == null) return@collectLatest
                    while (isActive) {
                        val period = if (newAccountFlow) 1.minutes else 12.hours
                        delay(if (refreshAccountInfo()) period else 30.seconds)
                    }
                }
        }
    }

    suspend fun refreshServiceStatus() {
        runCatching {
            val raw = invokeFfi("""{"apiServiceStatus":{}}""", 15_000L)
            _serviceStatus.value = jsonConfig.decodeFromString<ServiceStatusOutput>(raw)
        }
    }

    suspend fun purchasePlaySubscription(promoCode: String?): Boolean {
        val billingDetails =
            invokeFfi(jsonConfig.encodeToString(ManagerCmd.ApiGoogleBillingDetails(promoCode)), 30_000L).let {
                jsonConfig.decodeFromString<ManagerCmdOk.ApiGoogleBillingDetails>(it)
            }
        val tokens = activity.billingFacade.launchFlow(activity, billingDetails) ?: return false
        for (token in tokens) {
            uploadPurchaseToken(binder, token, promoCode)
        }
        refreshAccountInfo()
        return true
    }

    suspend fun createDebugBundle(feedback: String?): String {
        osStatusManager.update { this.debugBundleStatus.inProgress = true }
        val result = runCatching {
            val cacheDir = activity.cacheDir.absolutePath
            val cmd = ManagerCmd.CreateDebugBundle(feedback?.ifBlank { null }, collectBundleInfo(activity), cacheDir)
            val raw = invokeFfi(jsonConfig.encodeToString(cmd), 180_000L)
            jsonConfig.decodeFromString(String.serializer(), raw)
        }
        osStatusManager.update {
            this.debugBundleStatus.inProgress = false
            result.onSuccess { this.debugBundleStatus.latestPath = it }
        }
        return result.getOrThrow()
    }

    fun updateAndroidSettings(next: AndroidVpnSettings) {
        _androidSettings.value = next
        runCatching { binder.setAndroidSettings(jsonConfig.encodeToString(next)) }
            .onFailure { log.error("failed to push android settings: ${it.message}") }
    }
}

@Serializable data class ErrorHolder(val code: String)
