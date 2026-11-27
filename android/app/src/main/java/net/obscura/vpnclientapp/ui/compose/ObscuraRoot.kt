package net.obscura.vpnclientapp.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.obscura.vpnclientapp.BuildConfig
import net.obscura.vpnclientapp.R
import net.obscura.vpnclientapp.client.CoreStatus
import net.obscura.vpnclientapp.client.OneExit
import net.obscura.vpnclientapp.client.OneRelay
import net.obscura.vpnclientapp.client.RelaySelector
import net.obscura.vpnclientapp.client.TrafficStats
import net.obscura.vpnclientapp.services.AndroidVpnSettings
import net.obscura.vpnclientapp.ui.CoreRepository
import net.obscura.vpnclientapp.ui.ListLoadState
import net.obscura.vpnclientapp.ui.OsStatus
import net.obscura.vpnclientapp.ui.OsStatusManager
import net.obscura.vpnclientapp.ui.PreferencesManager
import net.obscura.vpnclientapp.ui.SecureWindowEffect

private enum class MainTab(
    val navView: OsStatus.NavigationView,
    val label: String,
    val iconRes: Int,
) {
    CONNECTION(OsStatus.NavigationView.Connection, "VPN", R.drawable.icon_connection),
    LOCATION(OsStatus.NavigationView.Location, "Locations", R.drawable.icon_location),
    ACCOUNT(OsStatus.NavigationView.Account, "Account", R.drawable.icon_account),
    SETTINGS(OsStatus.NavigationView.Settings, "Settings", R.drawable.icon_settings),
    ABOUT(OsStatus.NavigationView.About, "About", R.drawable.icon_about),
}

private data class RootHostDeps(
    val repo: CoreRepository,
    val status: CoreStatus,
    val osStatusManager: OsStatusManager,
    val preferencesManager: PreferencesManager,
    val startError: String?,
)

private data class ShellUiState(
    val status: CoreStatus,
    val osSnapshot: OsStatusManager.Snapshot,
    val exitsState: ListLoadState<OneExit>,
    val relaysState: ListLoadState<OneRelay>,
    val stats: TrafficStats?,
    val androidSettings: AndroidVpnSettings,
    val preferences: PreferencesManager.Preferences,
    val startError: String?,
    val locationMode: LocationTabMode,
)

@Composable
fun ObscuraRoot(
    repo: CoreRepository?,
    osStatusManager: OsStatusManager,
    preferencesManager: PreferencesManager,
    startError: String?,
) {
    val preferences by preferencesManager.state.collectAsState()

    ObscuraTheme(colorSchemePref = preferences.colorScheme) {
        SecureWindowEffect(preferences.secureAllScreens)
        if (repo == null) {
            SplashLoading()
        } else {
            val status by repo.status.collectAsState()
            val serviceStatus by repo.serviceStatus.collectAsState()
            val currentStatus = status
            val playBilling = BuildConfig.FLAVOR == "play"

            if (currentStatus == null) {
                SplashLoading()
            } else if (currentStatus.accountId == null || currentStatus.inNewAccountFlow) {
                LoginScreen(
                    repo = repo,
                    status = currentStatus,
                    trialAvailable = serviceStatus?.freeTrial?.available == true,
                    playBilling = playBilling,
                )
            } else {
                MainShell(
                    deps = RootHostDeps(repo, currentStatus, osStatusManager, preferencesManager, startError),
                    preferences = preferences,
                )
            }
        }
    }
}

@Composable
private fun SplashLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppIcon(64.dp)
            CircularProgressIndicator(color = ObscuraPalette.Orange)
        }
    }
}

private data class ShellCallbacks(
    val repo: CoreRepository,
    val onModeAndTab: (LocationTabMode, MainTab) -> Unit,
    val onUpdatePreferences: (PreferencesManager.Preferences) -> Unit,
)

@Composable
private fun MainShell(deps: RootHostDeps, preferences: PreferencesManager.Preferences) {
    val scope = rememberCoroutineScope()
    val repo = deps.repo
    val osSnapshot by deps.osStatusManager.snapshot.collectAsState()
    val exitsState by repo.exits.collectAsState()
    val relaysState by repo.relays.collectAsState()
    val stats by repo.trafficStats.collectAsState()
    val androidSettings by repo.androidSettings.collectAsState()
    var currentTab by remember { mutableStateOf(MainTab.CONNECTION) }
    var locationMode by remember { mutableStateOf(LocationTabMode.EXIT) }

    LaunchedEffect(osSnapshot.navigationView) {
        val target = MainTab.entries.find { it.navView == osSnapshot.navigationView }
        if (target != null) currentTab = target
    }

    val selectTab: (MainTab) -> Unit = { tab ->
        currentTab = tab
        deps.osStatusManager.update { this.navigationView = tab.navView }
    }
    val shellState =
        ShellUiState(
            status = deps.status,
            osSnapshot = osSnapshot,
            exitsState = exitsState,
            relaysState = relaysState,
            stats = stats,
            androidSettings = androidSettings,
            preferences = preferences,
            startError = deps.startError,
            locationMode = locationMode,
        )
    val callbacks =
        ShellCallbacks(
            repo = repo,
            onModeAndTab = { mode, tab ->
                locationMode = mode
                selectTab(tab)
            },
            onUpdatePreferences = { next -> scope.launch { deps.preferencesManager.write(next) } },
        )

    Scaffold(
        bottomBar = { ObscuraBottomBar(currentTab = currentTab, onSelect = selectTab) },
    ) { padding ->
        MainTabContent(tab = currentTab, state = shellState, padding = padding, callbacks = callbacks)
    }
}

@Composable
private fun MainTabContent(
    tab: MainTab,
    state: ShellUiState,
    padding: PaddingValues,
    callbacks: ShellCallbacks,
) {
    val playBilling = BuildConfig.FLAVOR == "play"
    when (tab) {
        MainTab.CONNECTION -> ConnectionTabView(callbacks, state, padding)
        MainTab.LOCATION ->
            LocationScreen(
                repo = callbacks.repo,
                state =
                    LocationScreenState(
                        status = state.status,
                        exits = state.exitsState,
                        relays = state.relaysState,
                        internetAvailable = state.osSnapshot.internetAvailable,
                        mode = state.locationMode,
                    ),
                contentPadding = padding,
                onModeChange = { mode -> callbacks.onModeAndTab(mode, MainTab.LOCATION) },
                onNavigateToConnection = { callbacks.onModeAndTab(state.locationMode, MainTab.CONNECTION) },
            )
        MainTab.ACCOUNT -> AccountScreen(callbacks.repo, state.status, playBilling, padding)
        MainTab.SETTINGS ->
            SettingsScreen(
                SettingsHostArgs(
                    callbacks.repo,
                    state.status,
                    state.androidSettings,
                    state.preferences,
                    callbacks.onUpdatePreferences,
                ),
                padding,
            )
        MainTab.ABOUT ->
            AboutScreen(
                repo = callbacks.repo,
                debugInProgress = state.osSnapshot.debugInProgress,
                latestDebugPath = state.osSnapshot.latestDebugBundlePath,
                contentPadding = padding,
            )
    }
}

@Composable
private fun ConnectionTabView(callbacks: ShellCallbacks, state: ShellUiState, padding: PaddingValues) {
    val scope = rememberCoroutineScope()
    val exitItems = (state.exitsState as? ListLoadState.Ready)?.items.orEmpty()
    val relayItems = (state.relaysState as? ListLoadState.Ready)?.items.orEmpty()
    ConnectionScreen(
        params =
            ConnectionUiParams(
                status = state.status,
                exits = exitItems,
                relays = relayItems,
                stats = state.stats,
                internetAvailable = state.osSnapshot.internetAvailable,
                startError = state.startError,
            ),
        actions =
            ConnectionActions(
                onConnect = { scope.launch { callbacks.repo.connect() } },
                onDisconnect = { callbacks.repo.disconnect() },
                onChooseEntry = { callbacks.onModeAndTab(LocationTabMode.ENTRY, MainTab.LOCATION) },
                onChooseExit = { callbacks.onModeAndTab(LocationTabMode.EXIT, MainTab.LOCATION) },
                onOpenAccount = { callbacks.onModeAndTab(state.locationMode, MainTab.ACCOUNT) },
                onSwitchMode = { mode ->
                    scope.launch { callbacks.repo.updateTunnelArgs(state.status.tunnelArgs.copy(mode = mode)) }
                },
                onUseFastestRelay = {
                    scope.launch {
                        callbacks.repo.updateTunnelArgs(state.status.tunnelArgs.copy(relay = RelaySelector.Any))
                    }
                },
            ),
        contentPadding = padding,
    )
}

@Composable
private fun ObscuraBottomBar(currentTab: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        MainTab.entries.forEach { tab ->
            val selected = tab == currentTab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = tab.label,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = ObscuraPalette.Orange,
                        selectedTextColor = ObscuraPalette.Orange,
                        unselectedIconColor = LocalExtraColors.current.muted,
                        unselectedTextColor = LocalExtraColors.current.muted,
                        indicatorColor = ObscuraPalette.Orange.copy(alpha = 0.14f),
                    ),
            )
        }
    }
}
