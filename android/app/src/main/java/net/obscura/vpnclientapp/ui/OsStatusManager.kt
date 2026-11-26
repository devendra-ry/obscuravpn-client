package net.obscura.vpnclientapp.ui

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.obscura.lib.util.Logger
import net.obscura.vpnclientapp.BuildConfig
import net.obscura.vpnclientapp.client.jsonConfig

private val log = Logger(OsStatusManager::class)

@Singleton
class OsStatusManager @Inject constructor(@ApplicationContext context: Context) :
    NetworkStatusObserver.Callback, PrivateDnsObserver.Callback {
    data class State(
        var colorScheme: OsStatus.ColorScheme = OsStatus.ColorScheme.Auto,
        var debugBundleStatus: OsStatus.DebugBundleStatus =
            OsStatus.DebugBundleStatus(
                inProgress = false,
                latestPath = null,
                inProgressCounter = 0,
            ),
        var internetAvailable: Boolean = false,
        var navigationView: OsStatus.NavigationView? = null,
        var playBilling: Boolean =
            @Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants") (BuildConfig.FLAVOR == "play"),
        var privateDnsActive: Boolean = false,
        var vpnStatus: OsStatus.OsVpnStatus = OsStatus.OsVpnStatus.Disconnected,
    )

    data class Snapshot(
        val internetAvailable: Boolean = false,
        val privateDnsActive: Boolean = false,
        val playBilling: Boolean = false,
        val colorScheme: OsStatus.ColorScheme = OsStatus.ColorScheme.Auto,
        val navigationView: OsStatus.NavigationView? = null,
        val vpnStatus: OsStatus.OsVpnStatus = OsStatus.OsVpnStatus.Disconnected,
        val debugInProgress: Boolean = false,
        val latestDebugBundlePath: String? = null,
    )

    private data class VersionedState(val version: UUID, val state: State)

    private var current = VersionedState(UUID.randomUUID(), State())
    private val waiting = ArrayList<CompletableDeferred<String>>()
    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    init {
        NetworkStatusObserver(context, this)
        PrivateDnsObserver(context, this)
    }

    override fun onAvailableNetworksChanged(availableNetworks: Int) {
        this.update { this.internetAvailable = availableNetworks > 0 }
    }

    override fun onPrivateDnsChanged(strictMode: Boolean) {
        this.update { this.privateDnsActive = strictMode }
    }

    @Synchronized
    fun update(block: State.() -> Unit = {}) {
        val version = UUID.randomUUID()
        val result = runCatching {
            block(this.current.state)
            val s = this.current.state
            _snapshot.value =
                Snapshot(
                    internetAvailable = s.internetAvailable,
                    privateDnsActive = s.privateDnsActive,
                    playBilling = s.playBilling,
                    colorScheme = s.colorScheme,
                    navigationView = s.navigationView,
                    vpnStatus = s.vpnStatus,
                    debugInProgress = s.debugBundleStatus.inProgress == true,
                    latestDebugBundlePath = s.debugBundleStatus.latestPath,
                )
            OsStatus(
                    version = version.toString(),
                    internetAvailable = s.internetAvailable,
                    osVpnStatus = s.vpnStatus,
                    srcVersion = BuildConfig.VERSION_NAME,
                    navigationView = s.navigationView,
                    colorScheme = s.colorScheme,
                    updaterStatus =
                        OsStatus.UpdaterStatus(
                            type = "uninitiated",
                            appcast = null,
                            error = null,
                            errorCode = null,
                        ),
                    debugBundleStatus = s.debugBundleStatus,
                    canSendMail = true,
                    loginItemStatus = null,
                    playBilling = s.playBilling,
                    privateDnsActive = s.privateDnsActive,
                )
                .let { jsonConfig.encodeToString(it) }
        }
        this.current = VersionedState(version, this.current.state)
        log.debug("updated OS status: ${this.current}")
        this.waiting.forEach { it.completeWith(result) }
        this.waiting.clear()
    }

    @Synchronized
    fun waitForUpdate(knownVersion: String?): Deferred<String> =
        CompletableDeferred<String>().also {
            this.waiting.add(it)
            if (this.current.version.toString() != knownVersion) {
                this.update()
            }
        }
}
