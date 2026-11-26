package net.obscura.vpnclientapp.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.net.InetAddress
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import net.obscura.vpnclientapp.client.CoreStatus
import net.obscura.vpnclientapp.client.DnsContentBlock
import net.obscura.vpnclientapp.client.TunnelMode
import net.obscura.vpnclientapp.services.AndroidVpnSettings
import net.obscura.vpnclientapp.services.BackgroundHealthHelper
import net.obscura.vpnclientapp.services.BackgroundHealthStatus
import net.obscura.vpnclientapp.services.SplitMode
import net.obscura.vpnclientapp.services.TunPlan
import net.obscura.vpnclientapp.services.VpnServiceRuntimeState
import net.obscura.vpnclientapp.services.VpnServiceStateRegistry
import net.obscura.vpnclientapp.ui.CoreRepository
import net.obscura.vpnclientapp.ui.OsStatus
import net.obscura.vpnclientapp.ui.PreferencesManager

private const val SECONDS_PER_DAY = 86_400L
private const val SECONDS_30_DAYS = 30L * SECONDS_PER_DAY

data class SettingsHostArgs(
    val repo: CoreRepository,
    val status: CoreStatus,
    val androidSettings: AndroidVpnSettings,
    val preferences: PreferencesManager.Preferences,
    val onUpdatePreferences: (PreferencesManager.Preferences) -> Unit,
)

@Composable
fun SettingsScreen(args: SettingsHostArgs, contentPadding: PaddingValues) {
    var showSplitAppSheet by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        SettingsCoreCards(args)
        SettingsAdvancedCards(args, onOpenApps = { showSplitAppSheet = true })
    }

    if (showSplitAppSheet) {
        SplitAppsSheet(
            selectedApps = args.androidSettings.splitApps,
            onUpdateApps = { next -> args.repo.updateAndroidSettings(args.androidSettings.copy(splitApps = next)) },
            onDismiss = { showSplitAppSheet = false },
        )
    }
}

@Composable
private fun SettingsCoreCards(args: SettingsHostArgs) {
    val scope = rememberCoroutineScope()
    GeneralSection(args.status.autoConnect) { enable -> scope.launch { args.repo.setAutoConnect(enable) } }
    ConnectionSection(
        status = args.status,
        onModeChange = { mode ->
            scope.launch { args.repo.updateTunnelArgs(args.status.tunnelArgs.copy(mode = mode)) }
        },
        onMtuChange = { mtu -> scope.launch { args.repo.updateTunnelArgs(args.status.tunnelArgs.copy(udpMtu = mtu)) } },
        onKeyAgeChange = { secs -> scope.launch { args.repo.setWgKeyMaxAge(secs) } },
    )
    DnsSection(
        block = args.status.dnsContentBlock,
        customDns = args.androidSettings.customDns,
        onBlockChange = { next -> scope.launch { args.repo.setDnsContentBlock(next) } },
        onCustomDnsChange = { next -> args.repo.updateAndroidSettings(args.androidSettings.copy(customDns = next)) },
    )
}

@Composable
private fun SettingsAdvancedCards(args: SettingsHostArgs, onOpenApps: () -> Unit) {
    val scope = rememberCoroutineScope()
    val runtimeState by VpnServiceStateRegistry.state.collectAsState()
    SystemBackgroundSection(runtimeState)
    SplitTunnelSection(
        settings = args.androidSettings,
        isLockdownEnabled = runtimeState.isLockdownEnabled,
        onModeChange = { mode -> args.repo.updateAndroidSettings(args.androidSettings.copy(splitMode = mode)) },
        onExcludedCidrsChange = { cidrs ->
            args.repo.updateAndroidSettings(args.androidSettings.copy(excludedCidrs = cidrs))
        },
        onOpenApps = onOpenApps,
    )
    NetworkSection(
        status = args.status,
        onLocalNetworkChange = { enable -> scope.launch { args.repo.setLocalNetworkAccess(enable) } },
        onRotateKey = { scope.launch { args.repo.rotateWgKey() } },
        onSetSni = { host -> scope.launch { args.repo.setSniRelay(host) } },
        onSetBackupApi = { host -> scope.launch { args.repo.setApiHostAlternate(host) } },
    )
    PrivacyDiagnosticsSection(
        secureAll = args.preferences.secureAllScreens,
        persistLogs = args.androidSettings.persistLogs,
        onSecureAllChange = { args.onUpdatePreferences(args.preferences.copy(secureAllScreens = it)) },
        onPersistLogsChange = { args.repo.updateAndroidSettings(args.androidSettings.copy(persistLogs = it)) },
    )
    if (args.status.featureFlagKeys.isNotEmpty()) {
        ExperimentalSection(args.status) { flag, active -> scope.launch { args.repo.setFeatureFlag(flag, active) } }
    }
    AppearanceSection(args.preferences.colorScheme) { scheme ->
        args.onUpdatePreferences(args.preferences.copy(colorScheme = scheme))
    }
}

@Composable
private fun GeneralSection(autoConnect: Boolean, onAutoConnectChange: (Boolean) -> Unit) {
    ObscuraCard {
        Text("General", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        SwitchSettingRow(
            title = "Auto-connect on launch",
            subtitle = "Connect automatically when opening Obscura",
            checked = autoConnect,
            onCheckedChange = onAutoConnectChange,
        )
    }
}

@Composable
private fun ConnectionSection(
    status: CoreStatus,
    onModeChange: (TunnelMode) -> Unit,
    onMtuChange: (Int?) -> Unit,
    onKeyAgeChange: (Long) -> Unit,
) {
    val mode = status.tunnelArgs.mode
    ObscuraCard {
        Text("Connection Protocol", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        RadioOptionRow(
            title = "Stealth (WireGuard® over QUIC)",
            subtitle = "Disguises traffic to bypass censorship",
            selected = mode == TunnelMode.QUIC,
            onClick = { onModeChange(TunnelMode.QUIC) },
        )
        RadioOptionRow(
            title = "Speed (Native WireGuard® UDP)",
            subtitle = "Lowest overhead and highest throughput",
            selected = mode == TunnelMode.WIREGUARD_UDP,
            onClick = { onModeChange(TunnelMode.WIREGUARD_UDP) },
        )
        RadioOptionRow(
            title = "TCP/TLS Fallback",
            subtitle = "Use on strict networks blocking UDP and QUIC",
            selected = mode == TunnelMode.TCP_TLS,
            onClick = { onModeChange(TunnelMode.TCP_TLS) },
        )
        if (mode == TunnelMode.WIREGUARD_UDP) {
            SectionDivider()
            SpeedMtuControl(status.tunnelArgs.udpMtu, onMtuChange)
        }
        SectionDivider()
        KeyRotationSection(status.wgKeyMaxAgeSecs, onKeyAgeChange)
    }
}

@Composable
private fun SpeedMtuControl(currentMtu: Int?, onMtuChange: (Int?) -> Unit) {
    var sliderVal by remember(currentMtu) { mutableFloatStateOf((currentMtu ?: 1420).toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Speed mode MTU", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onMtuChange(if (currentMtu == null) 1280 else null) }) {
                Text(if (currentMtu == null) "Auto" else "${currentMtu} B", color = ObscuraPalette.Orange)
            }
        }
        if (currentMtu != null) {
            Slider(
                value = sliderVal,
                onValueChange = { sliderVal = it },
                onValueChangeFinished = { onMtuChange(sliderVal.roundToInt().coerceIn(1280, 1420)) },
                valueRange = 1280f..1420f,
            )
        }
    }
}

@Composable
private fun KeyRotationSection(currentAgeSecs: Long, onKeyAgeChange: (Long) -> Unit) {
    Text("WireGuard® key rotation", style = MaterialTheme.typography.titleSmall)
    RadioOptionRow("Every 30 days (default)", null, currentAgeSecs >= SECONDS_30_DAYS) {
        onKeyAgeChange(SECONDS_30_DAYS)
    }
    RadioOptionRow("Every 24 hours", null, currentAgeSecs in 1L until SECONDS_30_DAYS) {
        onKeyAgeChange(SECONDS_PER_DAY)
    }
    RadioOptionRow("Every connection", null, currentAgeSecs == 0L) { onKeyAgeChange(0L) }
}

@Composable
private fun DnsSection(
    block: DnsContentBlock,
    customDns: List<String>,
    onBlockChange: (DnsContentBlock) -> Unit,
    onCustomDnsChange: (List<String>) -> Unit,
) {
    ObscuraCard {
        Text("DNS & Content Blocking", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        NoticeBanner(
            text =
                "Android Private DNS overrides Obscura DNS. Set Private DNS to Automatic or Off for tunnel filtering.",
            kind = NoticeKind.INFO,
        )
        Spacer(Modifier.height(6.dp))
        DnsBlockCheckboxes(block, onBlockChange)
        SectionDivider()
        CustomDnsEditor(customDns, onCustomDnsChange)
    }
}

@Composable
private fun DnsBlockCheckboxes(block: DnsContentBlock, onBlockChange: (DnsContentBlock) -> Unit) {
    CheckboxRow("Block Ads", block.ad) { onBlockChange(block.copy(ad = it)) }
    CheckboxRow("Block Trackers", block.tracker) { onBlockChange(block.copy(tracker = it)) }
    CheckboxRow("Block Malware", block.malware) { onBlockChange(block.copy(malware = it)) }
    CheckboxRow("Block Adult Content", block.adult) { onBlockChange(block.copy(adult = it)) }
    CheckboxRow("Block Gambling", block.gambling) { onBlockChange(block.copy(gambling = it)) }
    CheckboxRow("Block Social Media", block.socialMedia) { onBlockChange(block.copy(socialMedia = it)) }
}

private data class AddressEditorSpec(
    val title: String,
    val placeholder: String,
    val invalidMessage: String,
    val normalize: (String) -> String?,
)

@Composable
private fun CustomDnsEditor(customDns: List<String>, onCustomDnsChange: (List<String>) -> Unit) {
    AddressListEditor(
        spec =
            AddressEditorSpec(
                title = "Custom DNS servers",
                placeholder = "1.1.1.1 or 2606:4700:4700::1111",
                invalidMessage = "Enter a valid IPv4 or IPv6 address",
                normalize = { candidate -> candidate.takeIf(::isIpLiteral) },
            ),
        items = customDns,
        onItemsChange = onCustomDnsChange,
    )
}

@Composable
private fun ExcludedCidrsEditor(excludedCidrs: List<String>, onExcludedCidrsChange: (List<String>) -> Unit) {
    AddressListEditor(
        spec =
            AddressEditorSpec(
                title = "Excluded IPs & subnets (CIDR)",
                placeholder = "192.168.1.0/24 or 10.0.0.5",
                invalidMessage = "Enter a valid IPv4 or IPv6 IP/CIDR (prefix > 0)",
                normalize = { candidate -> TunPlan.parseExcludedCidr(candidate)?.let { "${it.address}/${it.prefix}" } },
            ),
        items = excludedCidrs,
        onItemsChange = onExcludedCidrsChange,
    )
}

@Composable
private fun AddressListEditor(
    spec: AddressEditorSpec,
    items: List<String>,
    onItemsChange: (List<String>) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    Text(spec.title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                inputError = null
            },
            placeholder = { Text(spec.placeholder) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                val normalized = spec.normalize(input.trim())
                if (normalized == null) {
                    inputError = spec.invalidMessage
                } else if (normalized !in items) {
                    onItemsChange(items + normalized)
                    input = ""
                } else {
                    input = ""
                }
            },
        ) {
            Text("Add")
        }
    }
    inputError?.let {
        Spacer(Modifier.height(6.dp))
        NoticeBanner(text = it, kind = NoticeKind.ERROR)
    }
    items.forEach { entry ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontMono),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onItemsChange(items - entry) }) {
                Icon(AppIcons.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SystemBackgroundSection(runtimeState: VpnServiceRuntimeState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var health by remember { mutableStateOf(BackgroundHealthHelper.query(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                health = BackgroundHealthHelper.query(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ObscuraCard {
        Text("System & Background", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        KeyValueRow(label = "Battery & background policy", value = backgroundSummaryText(health))
        if (!health.ignoringBatteryOptimizations || health.backgroundRestricted) {
            Spacer(Modifier.height(6.dp))
            NoticeBanner(
                text =
                    "Allow unrestricted background usage so Android or HyperOS " +
                        "does not kill the VPN tunnel while idle.",
                kind = NoticeKind.WARNING,
                actionLabel = "Allow unrestricted",
                onAction = { BackgroundHealthHelper.openBatterySettings(context) },
            )
        } else {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { BackgroundHealthHelper.openBatterySettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Battery settings")
            }
        }
        if (health.isXiaomiFamily) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { BackgroundHealthHelper.openXiaomiAutostartSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("HyperOS / MIUI Autostart")
            }
        }
        SectionDivider()
        KeyValueRow(label = "Android Always-On VPN", value = alwaysOnSummaryText(runtimeState))
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = { BackgroundHealthHelper.openVpnSystemSettings(context) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open Android VPN settings")
        }
    }
}

private fun backgroundSummaryText(health: BackgroundHealthStatus): String =
    when {
        health.backgroundRestricted -> "Restricted (OS may stop background VPN)"
        health.ignoringBatteryOptimizations -> "Unrestricted"
        else -> "Optimized (recommended: set to Unrestricted)"
    }

private fun alwaysOnSummaryText(state: VpnServiceRuntimeState): String =
    when {
        state.isLockdownEnabled -> "Always-On + Lockdown (blocks traffic outside VPN)"
        state.isAlwaysOn -> "Always-On enabled"
        else -> "Off"
    }

@Composable
private fun SplitTunnelSection(
    settings: AndroidVpnSettings,
    isLockdownEnabled: Boolean,
    onModeChange: (SplitMode) -> Unit,
    onExcludedCidrsChange: (List<String>) -> Unit,
    onOpenApps: () -> Unit,
) {
    ObscuraCard {
        Text("Split Tunnelling", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (isLockdownEnabled && (settings.splitMode != SplitMode.Off || settings.excludedCidrs.isNotEmpty())) {
            NoticeBanner(
                text =
                    "Android Lockdown mode is active. Apps or subnets bypassing the VPN " +
                        "will lose network access while Lockdown is enabled.",
                kind = NoticeKind.WARNING,
            )
            Spacer(Modifier.height(8.dp))
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SplitMode.entries.forEachIndexed { idx, mode ->
                SegmentedButton(
                    selected = settings.splitMode == mode,
                    onClick = { onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(idx, SplitMode.entries.size),
                ) {
                    Text(mode.name)
                }
            }
        }
        if (settings.splitMode != SplitMode.Off) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onOpenApps, modifier = Modifier.fillMaxWidth()) {
                val verb = if (settings.splitMode == SplitMode.Exclude) "Exclude" else "Include"
                Text("$verb apps (${settings.splitApps.size} selected)")
            }
        }
        SectionDivider()
        ExcludedCidrsEditor(settings.excludedCidrs, onExcludedCidrsChange)
    }
}

@Composable
private fun NetworkSection(
    status: CoreStatus,
    onLocalNetworkChange: (Boolean) -> Unit,
    onRotateKey: () -> Unit,
    onSetSni: (String?) -> Unit,
    onSetBackupApi: (String?) -> Unit,
) {
    var hostDialog by remember { mutableStateOf<String?>(null) }
    var hostTitle by remember { mutableStateOf("") }
    var hostTarget by remember { mutableStateOf<(String?) -> Unit>({}) }

    ObscuraCard {
        Text("Network & Advanced", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        SwitchSettingRow(
            title = "Allow local network access",
            subtitle = "Reach LAN printers, NAS devices, and local routers while connected",
            checked = status.localNetworkAccess,
            onCheckedChange = onLocalNetworkChange,
        )
        SectionDivider()
        OutlinedButton(onClick = onRotateKey, modifier = Modifier.fillMaxWidth()) { Text("Rotate WireGuard® key now") }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    hostTitle = "Relay SNI host"
                    hostDialog = ""
                    hostTarget = onSetSni
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Relay SNI")
            }
            OutlinedButton(
                onClick = {
                    hostTitle = "Backup API host"
                    hostDialog = status.apiUrl
                    hostTarget = onSetBackupApi
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Backup API")
            }
        }
    }

    hostDialog?.let { initial ->
        HostInputDialog(
            title = hostTitle,
            initial = initial,
            onDismiss = { hostDialog = null },
            onSave = { value ->
                hostTarget(value.ifBlank { null })
                hostDialog = null
            },
        )
    }
}

@Composable
private fun PrivacyDiagnosticsSection(
    secureAll: Boolean,
    persistLogs: Boolean,
    onSecureAllChange: (Boolean) -> Unit,
    onPersistLogsChange: (Boolean) -> Unit,
) {
    ObscuraCard {
        Text("Privacy & Diagnostics", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        SwitchSettingRow(
            title = "Block screenshots everywhere",
            subtitle = "Apply screen capture protection to all app screens",
            checked = secureAll,
            onCheckedChange = onSecureAllChange,
        )
        SwitchSettingRow(
            title = "Keep detailed logs",
            subtitle = "Store rolling diagnostic logs on device for debug bundles",
            checked = persistLogs,
            onCheckedChange = onPersistLogsChange,
        )
    }
}

@Composable
private fun ExperimentalSection(status: CoreStatus, onFlagChange: (String, Boolean) -> Unit) {
    ObscuraCard {
        Text("Experimental", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        status.featureFlagKeys.forEach { flag ->
            val current = resolveFlagValue(status, flag)
            SwitchSettingRow(
                title = flag,
                subtitle = null,
                checked = current,
                onCheckedChange = { onFlagChange(flag, it) },
            )
        }
    }
}

@Composable
private fun AppearanceSection(
    current: OsStatus.ColorScheme,
    onSelect: (OsStatus.ColorScheme) -> Unit,
) {
    ObscuraCard {
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options =
                listOf(
                    OsStatus.ColorScheme.Auto to "System",
                    OsStatus.ColorScheme.Light to "Light",
                    OsStatus.ColorScheme.Dark to "Dark",
                )
            options.forEachIndexed { idx, (scheme, label) ->
                SegmentedButton(
                    selected = current == scheme,
                    onClick = { onSelect(scheme) },
                    shape = SegmentedButtonDefaults.itemShape(idx, options.size),
                ) {
                    Text(label)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitAppsSheet(
    selectedApps: Set<String>,
    onUpdateApps: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<net.obscura.vpnclientapp.ui.InstalledApps.Item>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { apps = net.obscura.vpnclientapp.ui.InstalledApps.query(context, context.packageName) }

    val filtered =
        remember(apps, search, showSystem) {
            val q = search.trim().lowercase()
            apps.filter { app ->
                (showSystem || !app.isSystem) &&
                    (q.isEmpty() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q))
            }
        }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Choose apps", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Filter installed apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            CheckboxRow("Show system apps", showSystem) { showSystem = it }
            LazyColumn(modifier = Modifier.height(380.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in selectedApps
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    val next =
                                        if (checked) selectedApps - app.packageName else selectedApps + app.packageName
                                    onUpdateApps(next)
                                }
                                .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(app.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalExtraColors.current.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text("Leave blank for default") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onSave(value.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.muted)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RadioOptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.muted)
            }
        }
    }
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun resolveFlagValue(status: CoreStatus, key: String): Boolean =
    when (key) {
        "quicFramePadding" -> status.featureFlags.quicFramePadding == true
        "killSwitch" -> status.featureFlags.killSwitch == true
        "forceSmallMtu" -> status.featureFlags.forceSmallMtu == true
        "tcpTlsTunnel" -> status.featureFlags.tcpTlsTunnel == true
        else -> false
    }

private fun isIpLiteral(input: String): Boolean {
    if (input.isBlank() || input.any { it.isLetter() && it !in 'a'..'f' && it !in 'A'..'F' && it != ':' }) {
        return false
    }
    return runCatching { InetAddress.getByName(input) }.isSuccess
}
