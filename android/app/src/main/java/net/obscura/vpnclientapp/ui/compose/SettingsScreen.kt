package net.obscura.vpnclientapp.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
private const val MTU_MIN = 1280
private const val MTU_MAX = 1420

data class SettingsHostArgs(
    val repo: CoreRepository,
    val status: CoreStatus,
    val androidSettings: AndroidVpnSettings,
    val preferences: PreferencesManager.Preferences,
    val onUpdatePreferences: (PreferencesManager.Preferences) -> Unit,
)

/** Grouped lists as in the location picker: a caption, then rows in one card. */
@Composable
fun SettingsScreen(args: SettingsHostArgs, contentPadding: PaddingValues) {
    var showSplitAppSheet by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        ConnectionGroups(args)
        DnsGroup(args)
        SplitTunnelGroup(args, onOpenApps = { showSplitAppSheet = true })
        SystemGroups(args)
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
private fun ConnectionGroups(args: SettingsHostArgs) {
    val scope = rememberCoroutineScope()
    val status = args.status
    GroupLabel("Connection")
    RowGroup {
        SwitchRow("Auto-connect", "Connect when Obscura opens", status.autoConnect) { on ->
            scope.launch { args.repo.setAutoConnect(on) }
        }
        GroupDivider()
        SwitchRow(
            "Local network access",
            "Reach printers, NAS and your router while connected",
            status.localNetworkAccess,
        ) { on ->
            scope.launch { args.repo.setLocalNetworkAccess(on) }
        }
    }
    LearnMoreLabel("Protocol", LearnMore.PROTOCOL)
    RowGroup {
        ProtocolRows(args)
        GroupDivider()
        SwitchRow(
            "Switch automatically",
            "Try the fastest relay or another protocol when one cannot connect",
            args.preferences.autoSwitchProtocol,
        ) {
            args.onUpdatePreferences(args.preferences.copy(autoSwitchProtocol = it))
        }
    }
}

@Composable
private fun ProtocolRows(args: SettingsHostArgs) {
    val scope = rememberCoroutineScope()
    val mode = args.status.tunnelArgs.mode
    val setMode: (TunnelMode) -> Unit = { next ->
        scope.launch { args.repo.updateTunnelArgs(args.status.tunnelArgs.copy(mode = next)) }
    }
    val setFlag: (String, Boolean) -> Unit = { flag, on -> scope.launch { args.repo.setFeatureFlag(flag, on) } }
    ChoiceRow("Stealth", "WireGuard® over QUIC: looks like web traffic", mode == TunnelMode.QUIC) {
        setMode(TunnelMode.QUIC)
    }
    GroupDivider()
    ChoiceRow("Speed", "Native WireGuard® over UDP: fastest", mode == TunnelMode.WIREGUARD_UDP) {
        setMode(TunnelMode.WIREGUARD_UDP)
    }
    GroupDivider()
    ChoiceRow("TCP/TLS", "For networks that block UDP and QUIC", mode == TunnelMode.TCP_TLS) {
        setMode(TunnelMode.TCP_TLS)
    }
    when (mode) {
        TunnelMode.QUIC -> {
            GroupDivider()
            SwitchRow(
                "Packet padding",
                "Uniform packet sizes; uses more upload",
                args.status.featureFlags.quicFramePadding == true,
            ) {
                setFlag("quicFramePadding", it)
            }
            GroupDivider()
            SwitchRow(
                "Small packets",
                "For networks that drop large packets",
                args.status.featureFlags.forceSmallMtu == true,
            ) {
                setFlag("forceSmallMtu", it)
            }
        }
        TunnelMode.WIREGUARD_UDP -> {
            GroupDivider()
            MtuControl(args.status.tunnelArgs.udpMtu) { mtu ->
                scope.launch { args.repo.updateTunnelArgs(args.status.tunnelArgs.copy(udpMtu = mtu)) }
            }
        }
        TunnelMode.TCP_TLS -> Unit
    }
}

@Composable
private fun MtuControl(currentMtu: Int?, onMtuChange: (Int?) -> Unit) {
    var slider by remember(currentMtu) { mutableFloatStateOf((currentMtu ?: MTU_MAX).toFloat()) }
    GroupBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Packet size (MTU)", style = MaterialTheme.typography.bodyLarge)
                SupportingText(if (currentMtu == null) "Automatic" else "${slider.roundToInt()} bytes")
            }
            TextButton(onClick = { onMtuChange(if (currentMtu == null) MTU_MIN else null) }) {
                Text(if (currentMtu == null) "Set" else "Auto", color = ObscuraPalette.Orange)
            }
        }
        if (currentMtu != null) {
            Slider(
                value = slider,
                onValueChange = { slider = it },
                onValueChangeFinished = { onMtuChange(slider.roundToInt().coerceIn(MTU_MIN, MTU_MAX)) },
                valueRange = MTU_MIN.toFloat()..MTU_MAX.toFloat(),
            )
        }
    }
}

@Composable
private fun DnsGroup(args: SettingsHostArgs) {
    val scope = rememberCoroutineScope()
    val useSystemDns = args.status.useSystemDns
    var confirmSystemDns by remember { mutableStateOf(false) }
    LearnMoreLabel("DNS", LearnMore.DNS)
    RowGroup {
        ChoiceRow("Obscura DNS", "Lookups go through the tunnel", !useSystemDns) {
            scope.launch { args.repo.setUseSystemDns(false) }
        }
        GroupDivider()
        ChoiceRow("Network's DNS", "Lookups go outside the tunnel; your network sees them", useSystemDns) {
            if (!useSystemDns) confirmSystemDns = true
        }
        GroupDivider()
        ContentBlockChips(args.status.dnsContentBlock, enabled = !useSystemDns) { next ->
            scope.launch { args.repo.setDnsContentBlock(next) }
        }
        GroupDivider()
        AddressListEditor(
            spec =
                AddressEditorSpec(
                    title = "Custom DNS servers",
                    placeholder = "1.1.1.1 or 2606:4700::1111",
                    invalidMessage = "Enter an IPv4 or IPv6 address",
                    normalize = { candidate -> candidate.takeIf(::isIpLiteral) },
                ),
            items = args.androidSettings.customDns,
            enabled = !useSystemDns,
            onItemsChange = { next -> args.repo.updateAndroidSettings(args.androidSettings.copy(customDns = next)) },
        )
    }
    GroupFooter(
        "Android's Private DNS overrides these. Set it to Automatic or Off for blocking and custom servers to apply.",
    )
    if (confirmSystemDns) {
        ConfirmDialog(
            title = "Send DNS outside the tunnel?",
            text = "Your network provider will see the names you look up. Blocking and custom servers will not apply.",
            confirm = "Use network DNS",
            onConfirm = {
                confirmSystemDns = false
                scope.launch { args.repo.setUseSystemDns(true) }
            },
            onDismiss = { confirmSystemDns = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentBlockChips(block: DnsContentBlock, enabled: Boolean, onChange: (DnsContentBlock) -> Unit) {
    val chips =
        listOf(
            "Ads" to (block.ad to block.copy(ad = !block.ad)),
            "Trackers" to (block.tracker to block.copy(tracker = !block.tracker)),
            "Malware" to (block.malware to block.copy(malware = !block.malware)),
            "Adult" to (block.adult to block.copy(adult = !block.adult)),
            "Gambling" to (block.gambling to block.copy(gambling = !block.gambling)),
            "Social media" to (block.socialMedia to block.copy(socialMedia = !block.socialMedia)),
        )
    GroupBlock {
        Text("Block", style = MaterialTheme.typography.bodyLarge)
        SupportingText(if (enabled) "Filtered by Obscura's DNS" else "Needs Obscura DNS")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            chips.forEach { (label, state) ->
                val (on, toggled) = state
                FilterChip(
                    selected = on,
                    onClick = { onChange(toggled) },
                    label = { Text(label) },
                    enabled = enabled,
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                )
            }
        }
    }
}

@Composable
private fun SplitTunnelGroup(args: SettingsHostArgs, onOpenApps: () -> Unit) {
    val runtimeState by VpnServiceStateRegistry.state.collectAsState()
    val settings = args.androidSettings
    val update: (AndroidVpnSettings) -> Unit = { args.repo.updateAndroidSettings(it) }
    LearnMoreLabel("Split tunnelling", LearnMore.SPLIT)
    if (
        runtimeState.isLockdownEnabled && (settings.splitMode != SplitMode.Off || settings.excludedCidrs.isNotEmpty())
    ) {
        NoticeBanner(
            text = "Android's Lockdown mode is on: apps and subnets that bypass the VPN will have no network.",
            kind = NoticeKind.WARNING,
        )
    }
    RowGroup {
        GroupBlock {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes =
                    listOf(SplitMode.Off to "All apps", SplitMode.Exclude to "Exclude", SplitMode.Include to "Only")
                modes.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = settings.splitMode == mode,
                        onClick = { update(settings.copy(splitMode = mode)) },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) {
                        Text(label)
                    }
                }
            }
        }
        if (settings.splitMode != SplitMode.Off) {
            GroupDivider()
            val verb = if (settings.splitMode == SplitMode.Exclude) "Apps outside the VPN" else "Apps in the VPN"
            NavRow(verb, "${settings.splitApps.size} chosen", onClick = onOpenApps)
        }
        GroupDivider()
        AddressListEditor(
            spec =
                AddressEditorSpec(
                    title = "Excluded IPs and subnets",
                    placeholder = "192.168.1.0/24 or 10.0.0.5",
                    invalidMessage = "Enter an IP address or CIDR",
                    normalize = { c -> TunPlan.parseExcludedCidr(c)?.let { "${it.address}/${it.prefix}" } },
                ),
            items = settings.excludedCidrs,
            enabled = true,
            onItemsChange = { update(settings.copy(excludedCidrs = it)) },
        )
    }
}

@Composable
private fun SystemGroups(args: SettingsHostArgs) {
    val scope = rememberCoroutineScope()
    SecurityGroup(args)
    BackgroundGroup()
    GroupLabel("Privacy")
    RowGroup {
        SwitchRow("Block screenshots", "On every screen of the app", args.preferences.secureAllScreens) {
            args.onUpdatePreferences(args.preferences.copy(secureAllScreens = it))
        }
        GroupDivider()
        SwitchRow("Keep detailed logs", "Stored on this device for debug bundles", args.androidSettings.persistLogs) {
            args.repo.updateAndroidSettings(args.androidSettings.copy(persistLogs = it))
        }
    }
    AdvancedGroup(args)
    val experimental = args.status.featureFlagKeys.filter { it in EXPERIMENTAL_FLAGS }
    if (experimental.isNotEmpty()) {
        GroupLabel("Experimental")
        RowGroup {
            experimental.forEachIndexed { i, flag ->
                if (i > 0) GroupDivider()
                val (title, subtitle) = EXPERIMENTAL_FLAGS.getValue(flag)
                SwitchRow(title, subtitle, flagValue(args.status, flag)) { on ->
                    scope.launch { args.repo.setFeatureFlag(flag, on) }
                }
            }
        }
    }
    AppearanceGroup(args.preferences.colorScheme) { args.onUpdatePreferences(args.preferences.copy(colorScheme = it)) }
}

@Composable
private fun SecurityGroup(args: SettingsHostArgs) {
    val scope = rememberCoroutineScope()
    val age = args.status.wgKeyMaxAgeSecs
    val setAge: (Long) -> Unit = { secs -> scope.launch { args.repo.setWgKeyMaxAge(secs) } }
    var rotated by remember { mutableStateOf(false) }
    LearnMoreLabel("WireGuard® key", LearnMore.KEY)
    RowGroup {
        ChoiceRow("New key every 30 days", "The default", age >= SECONDS_30_DAYS) { setAge(SECONDS_30_DAYS) }
        GroupDivider()
        ChoiceRow("New key every day", null, age in 1L until SECONDS_30_DAYS) { setAge(SECONDS_PER_DAY) }
        GroupDivider()
        ChoiceRow("New key every connection", "Slower to connect", age == 0L) { setAge(0L) }
        GroupDivider()
        ActionRow(if (rotated) "Key rotated" else "Rotate key now") {
            scope.launch {
                args.repo.rotateWgKey()
                rotated = true
            }
        }
    }
}

@Composable
private fun BackgroundGroup() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val runtimeState by VpnServiceStateRegistry.state.collectAsState()
    var health by remember { mutableStateOf(BackgroundHealthHelper.query(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) health = BackgroundHealthHelper.query(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val restricted = health.backgroundRestricted || !health.ignoringBatteryOptimizations
    GroupLabel("Android")
    RowGroup {
        NavRow("Battery use", backgroundSummaryText(health), warn = restricted) {
            BackgroundHealthHelper.openBatterySettings(context)
        }
        if (health.isXiaomiFamily) {
            GroupDivider()
            NavRow("Autostart (HyperOS / MIUI)", "Let Obscura start in the background") {
                BackgroundHealthHelper.openXiaomiAutostartSettings(context)
            }
        }
        GroupDivider()
        NavRow("Always-on VPN", alwaysOnSummaryText(runtimeState)) {
            BackgroundHealthHelper.openVpnSystemSettings(context)
        }
    }
    if (restricted) GroupFooter("Set battery use to Unrestricted so Android does not stop the VPN while idle.")
}

@Composable
private fun AdvancedGroup(args: SettingsHostArgs) {
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<HostDialog?>(null) }
    GroupLabel("Advanced")
    RowGroup {
        NavRow("Relay SNI", "The name the relay connection presents") {
            dialog = HostDialog("Relay SNI host", "") { host -> scope.launch { args.repo.setSniRelay(host) } }
        }
        GroupDivider()
        NavRow("Backup API host", args.status.apiUrl.ifBlank { null }) {
            dialog =
                HostDialog("Backup API host", args.status.apiUrl) { host ->
                    scope.launch { args.repo.setApiHostAlternate(host) }
                }
        }
    }
    dialog?.let { d ->
        HostInputDialog(
            title = d.title,
            initial = d.initial,
            onDismiss = { dialog = null },
            onSave = { value ->
                d.save(value.ifBlank { null })
                dialog = null
            },
        )
    }
}

private class HostDialog(val title: String, val initial: String, val save: (String?) -> Unit)

@Composable
private fun AppearanceGroup(current: OsStatus.ColorScheme, onSelect: (OsStatus.ColorScheme) -> Unit) {
    GroupLabel("Appearance")
    RowGroup {
        GroupBlock {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options =
                    listOf(
                        OsStatus.ColorScheme.Auto to "System",
                        OsStatus.ColorScheme.Light to "Light",
                        OsStatus.ColorScheme.Dark to "Dark",
                    )
                options.forEachIndexed { i, (scheme, label) ->
                    SegmentedButton(
                        selected = current == scheme,
                        onClick = { onSelect(scheme) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

/** Flags shown under Experimental, with names people can read; the stealth flags live under Protocol. */
private val EXPERIMENTAL_FLAGS =
    mapOf(
        "killSwitch" to ("Kill switch" to "Block all traffic while the tunnel is down"),
        "tcpTlsTunnel" to ("Stealth over TCP/TLS" to "Carry Stealth over TCP/TLS instead of QUIC"),
    )

private fun flagValue(status: CoreStatus, key: String): Boolean =
    when (key) {
        "killSwitch" -> status.featureFlags.killSwitch == true
        "tcpTlsTunnel" -> status.featureFlags.tcpTlsTunnel == true
        else -> false
    }

private fun backgroundSummaryText(health: BackgroundHealthStatus): String =
    when {
        health.backgroundRestricted -> "Restricted: Android may stop the VPN"
        health.ignoringBatteryOptimizations -> "Unrestricted"
        else -> "Optimized: set to Unrestricted"
    }

private fun alwaysOnSummaryText(state: VpnServiceRuntimeState): String =
    when {
        state.isLockdownEnabled -> "On, blocking connections without VPN"
        state.isAlwaysOn -> "On"
        else -> "Off"
    }

private fun isIpLiteral(input: String): Boolean {
    if (input.isBlank() || input.any { it.isLetter() && it !in 'a'..'f' && it !in 'A'..'F' && it != ':' }) return false
    return runCatching { InetAddress.getByName(input) }.isSuccess
}
