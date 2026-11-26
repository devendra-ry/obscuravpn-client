package net.obscura.vpnclientapp.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.obscura.vpnclientapp.client.AccountIdUtils
import net.obscura.vpnclientapp.client.CoreStatus
import net.obscura.vpnclientapp.client.ExitSelector
import net.obscura.vpnclientapp.client.OneExit
import net.obscura.vpnclientapp.client.OneRelay
import net.obscura.vpnclientapp.client.PinnedLocation
import net.obscura.vpnclientapp.client.RelaySelector
import net.obscura.vpnclientapp.ui.CoreRepository
import net.obscura.vpnclientapp.ui.ListLoadState

enum class LocationTabMode {
    EXIT,
    ENTRY,
}

private data class ExitCityGroup(
    val countryCode: String,
    val cityCode: String,
    val cityName: String,
    val exits: List<OneExit>,
)

private data class ExitCountryGroup(
    val countryCode: String,
    val countryName: String,
    val cities: List<ExitCityGroup>,
)

private data class RelayCityGroup(
    val countryCode: String,
    val cityCode: String,
    val cityName: String,
    val relays: List<OneRelay>,
)

private data class RelayCountryGroup(
    val countryCode: String,
    val countryName: String,
    val cities: List<RelayCityGroup>,
)

data class LocationScreenState(
    val status: CoreStatus,
    val exits: ListLoadState<OneExit>,
    val relays: ListLoadState<OneRelay>,
    val internetAvailable: Boolean,
    val mode: LocationTabMode,
)

private data class PickerSharedState(
    val repo: CoreRepository,
    val status: CoreStatus,
    val query: String,
    val expandedCountries: MutableMap<String, Boolean>,
    val expandedCities: MutableMap<String, Boolean>,
    val onSelected: () -> Unit,
)

@Composable
fun LocationScreen(
    repo: CoreRepository,
    state: LocationScreenState,
    contentPadding: PaddingValues,
    onModeChange: (LocationTabMode) -> Unit,
    onNavigateToConnection: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val expandedCountries = remember { mutableStateMapOf<String, Boolean>() }
    val expandedCities = remember { mutableStateMapOf<String, Boolean>() }
    val shared = PickerSharedState(repo, state.status, query, expandedCountries, expandedCities, onNavigateToConnection)

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LocationStatusStrip(
            status = state.status,
            internetAvailable = state.internetAvailable,
            onDisconnect = { repo.disconnect() },
            onConnect = {
                scope.launch {
                    repo.connect()
                    onNavigateToConnection()
                }
            },
        )
        LocationModeToggle(mode = state.mode, onModeChange = onModeChange)
        SearchBox(query = query, mode = state.mode, onQueryChange = { query = it })
        if (state.mode == LocationTabMode.EXIT) {
            ExitPickerList(shared = shared, exitsState = state.exits)
        } else {
            RelayPickerList(shared = shared, relaysState = state.relays)
        }
    }
}

@Composable
private fun SearchBox(query: String, mode: LocationTabMode, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                if (mode == LocationTabMode.EXIT) "Search exit country, city, or server" else "Search entry relay",
            )
        },
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(AppIcons.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LocationModeToggle(mode: LocationTabMode, onModeChange: (LocationTabMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == LocationTabMode.EXIT,
            onClick = { onModeChange(LocationTabMode.EXIT) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text("Exit")
        }
        SegmentedButton(
            selected = mode == LocationTabMode.ENTRY,
            onClick = { onModeChange(LocationTabMode.ENTRY) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text("Entry relay")
        }
    }
}

@Composable
private fun LocationStatusStrip(
    status: CoreStatus,
    internetAvailable: Boolean,
    onDisconnect: () -> Unit,
    onConnect: () -> Unit,
) {
    val vpn = status.vpnStatus
    val connected = vpn.connected
    val isConnecting = vpn.connecting != null
    val extra = LocalExtraColors.current
    val dotColor =
        when {
            connected != null -> extra.success
            isConnecting -> ObscuraPalette.Orange
            !internetAvailable -> MaterialTheme.colorScheme.error
            else -> extra.muted
        }
    val headline =
        when {
            connected != null -> "Connected to ${connected.exit.cityName}"
            isConnecting -> "Connecting…"
            !internetAvailable -> "No internet connection"
            else -> "Disconnected"
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extra.border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(dotColor))
            Text(
                headline,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (connected != null || isConnecting) {
                OutlinedButton(
                    onClick = onDisconnect,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = internetAvailable,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("Connect")
                }
            }
        }
    }
}

@Composable
private fun ExitPickerList(shared: PickerSharedState, exitsState: ListLoadState<OneExit>) {
    val scope = rememberCoroutineScope()
    when (exitsState) {
        is ListLoadState.Loading -> LoadingBox("Loading exit locations…")
        is ListLoadState.Error ->
            ErrorRetryBox("Failed to load locations (${exitsState.code})") {
                scope.launch { shared.repo.refreshExits() }
            }
        is ListLoadState.Ready -> ExitReadyList(shared, exitsState.items)
    }
}

@Composable
private fun ExitReadyList(shared: PickerSharedState, allExits: List<OneExit>) {
    val scope = rememberCoroutineScope()
    val groups = remember(allExits, shared.query) { buildExitTree(allExits, shared.query) }
    val pinned =
        remember(shared.status.pinnedLocations, allExits, shared.query) {
            filterPinned(shared.status.pinnedLocations, allExits, shared.query)
        }
    val selectExit: (ExitSelector) -> Unit = { selector ->
        scope.launch {
            val saved = shared.repo.updateTunnelArgs(shared.status.tunnelArgs.copy(exit = selector))
            if (saved.isSuccess && shared.status.vpnStatus.disconnected == null) {
                shared.onSelected()
            }
        }
    }
    val togglePin: (String, String) -> Unit = { country, city ->
        scope.launch { shared.repo.setPinnedExits(togglePinnedList(shared.status.pinnedLocations, country, city)) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "fastest_exit") {
            FastestLocationCard(
                title = "Fastest exit",
                subtitle = "Automatically picks the quickest server",
                selected = shared.status.tunnelArgs.exit.any != null,
                onClick = { selectExit(ExitSelector.Any) },
            )
        }
        if (pinned.isNotEmpty()) {
            item(key = "pinned_hdr") { SectionCaption("Pinned") }
            items(pinned, key = { "pin_${it.countryCode}_${it.cityCode}" }) { pin ->
                val isSel =
                    shared.status.tunnelArgs.exit.city?.let {
                        it.countryCode == pin.countryCode && it.cityCode == pin.cityCode
                    } == true
                PinnedCityRow(
                    pin = pin,
                    exits = allExits,
                    selected = isSel,
                    onSelect = { selectExit(ExitSelector.forCity(pin.countryCode, pin.cityCode)) },
                    onUnpin = { togglePin(pin.countryCode, pin.cityCode) },
                )
            }
        }
        item(key = "all_exits_hdr") { SectionCaption("All locations") }
        items(groups, key = { it.countryCode }) { country -> ExitCountryCard(country, shared, selectExit, togglePin) }
    }
}

@Composable
private fun ExitCountryCard(
    country: ExitCountryGroup,
    shared: PickerSharedState,
    onSelectExit: (ExitSelector) -> Unit,
    onTogglePin: (String, String) -> Unit,
) {
    val forceExpand = shared.query.isNotBlank()
    val expanded = forceExpand || shared.expandedCountries[country.countryCode] == true
    val activeExit = shared.status.vpnStatus.connected?.exit
    val isSelected = shared.status.tunnelArgs.exit.country?.countryCode == country.countryCode
    val isConnected = activeExit?.countryCode == country.countryCode

    ObscuraCard(contentPadding = PaddingValues(0.dp)) {
        CountryHeaderRow(
            header =
                CountryHeaderState(
                    country.countryCode,
                    country.countryName,
                    country.cities.size,
                    badgeFor(isConnected, isSelected),
                    expanded,
                ),
            onSelect = { onSelectExit(ExitSelector.forCountry(country.countryCode)) },
            onToggle = { shared.expandedCountries[country.countryCode] = !expanded },
        )
        if (expanded) {
            country.cities.forEach { city ->
                SectionDivider()
                ExitCitySection(city, shared, onSelectExit, onTogglePin)
            }
        }
    }
}

private enum class RowStatusBadge {
    NONE,
    SELECTED,
    CONNECTED,
}

private data class CountryHeaderState(
    val countryCode: String,
    val countryName: String,
    val cityCount: Int,
    val statusBadge: RowStatusBadge,
    val expanded: Boolean,
)

private fun badgeFor(connected: Boolean, selected: Boolean): RowStatusBadge =
    when {
        connected -> RowStatusBadge.CONNECTED
        selected -> RowStatusBadge.SELECTED
        else -> RowStatusBadge.NONE
    }

@Composable
private fun CountryHeaderRow(
    header: CountryHeaderState,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(AccountIdUtils.countryFlagEmoji(header.countryCode), fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(header.countryName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${header.cityCount} ${if (header.cityCount == 1) "city" else "cities"}",
                style = MaterialTheme.typography.bodySmall,
                color = LocalExtraColors.current.muted,
            )
        }
        if (header.statusBadge != RowStatusBadge.NONE) {
            ActiveBadge(connected = header.statusBadge == RowStatusBadge.CONNECTED)
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (header.expanded) AppIcons.ChevronDown else AppIcons.ChevronRight,
                contentDescription = "Expand",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ExitCitySection(
    city: ExitCityGroup,
    shared: PickerSharedState,
    onSelectExit: (ExitSelector) -> Unit,
    onTogglePin: (String, String) -> Unit,
) {
    val cityKey = "${city.countryCode}_${city.cityCode}"
    val expanded = shared.query.isNotBlank() || shared.expandedCities[cityKey] == true
    val isPinned =
        shared.status.pinnedLocations.any { it.countryCode == city.countryCode && it.cityCode == city.cityCode }
    val isCityConnected =
        shared.status.vpnStatus.connected?.exit?.let {
            it.countryCode == city.countryCode && it.cityCode == city.cityCode
        } == true
    val isCitySelected =
        shared.status.tunnelArgs.exit.city?.let {
            it.countryCode == city.countryCode && it.cityCode == city.cityCode
        } == true

    ExitCityHeaderRow(
        city = city,
        badge = badgeFor(isCityConnected, isCitySelected),
        isPinned = isPinned,
        expanded = expanded,
        onActions =
            Triple(
                { onSelectExit(ExitSelector.forCity(city.countryCode, city.cityCode)) },
                { onTogglePin(city.countryCode, city.cityCode) },
                { shared.expandedCities[cityKey] = !expanded },
            ),
    )
    if (expanded) {
        city.exits.forEach { exit ->
            val connectedHere = shared.status.vpnStatus.connected?.exit?.id == exit.id
            val selectedHere = shared.status.tunnelArgs.exit.exit?.id == exit.id
            ServerItemRow(
                title = exit.id,
                subtitle = exit.providerName.ifBlank { null },
                badge = badgeFor(connectedHere, selectedHere),
                onClick = { onSelectExit(ExitSelector.forExit(exit.id)) },
            )
        }
    }
}

@Composable
private fun ExitCityHeaderRow(
    city: ExitCityGroup,
    badge: RowStatusBadge,
    isPinned: Boolean,
    expanded: Boolean,
    onActions: Triple<() -> Unit, () -> Unit, () -> Unit>,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onActions.first)
                .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(city.cityName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${city.exits.size} ${if (city.exits.size == 1) "server" else "servers"}",
                style = MaterialTheme.typography.bodySmall,
                color = LocalExtraColors.current.muted,
            )
        }
        if (badge != RowStatusBadge.NONE) {
            ActiveBadge(connected = badge == RowStatusBadge.CONNECTED)
        }
        IconButton(onClick = onActions.second) {
            Icon(
                AppIcons.Pin,
                contentDescription = if (isPinned) "Unpin" else "Pin",
                tint = if (isPinned) ObscuraPalette.Orange else LocalExtraColors.current.muted,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onActions.third) {
            Icon(
                if (expanded) AppIcons.ChevronDown else AppIcons.ChevronRight,
                contentDescription = "Servers",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun RelayPickerList(shared: PickerSharedState, relaysState: ListLoadState<OneRelay>) {
    val scope = rememberCoroutineScope()
    when (relaysState) {
        is ListLoadState.Loading -> LoadingBox("Loading entry relays…")
        is ListLoadState.Error ->
            ErrorRetryBox("Failed to load relays (${relaysState.code})") {
                scope.launch { shared.repo.refreshRelays() }
            }
        is ListLoadState.Ready -> {
            val groups = remember(relaysState.items, shared.query) { buildRelayTree(relaysState.items, shared.query) }
            val selectRelay: (RelaySelector) -> Unit = { selector ->
                scope.launch {
                    val saved = shared.repo.updateTunnelArgs(shared.status.tunnelArgs.copy(relay = selector))
                    if (saved.isSuccess && shared.status.vpnStatus.disconnected == null) {
                        shared.onSelected()
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item(key = "fastest_relay") {
                    FastestLocationCard(
                        title = "Fastest entry relay",
                        subtitle = "Picked automatically for low latency",
                        selected = shared.status.tunnelArgs.relay.any != null,
                        onClick = { selectRelay(RelaySelector.Any) },
                    )
                }
                item(key = "all_relays_hdr") { SectionCaption("Entry relays") }
                items(groups, key = { it.countryCode }) { country -> RelayCountryCard(country, shared, selectRelay) }
            }
        }
    }
}

@Composable
private fun RelayCountryCard(
    country: RelayCountryGroup,
    shared: PickerSharedState,
    onSelectRelay: (RelaySelector) -> Unit,
) {
    val forceExpand = shared.query.isNotBlank()
    val key = "r_${country.countryCode}"
    val expanded = forceExpand || shared.expandedCountries[key] == true
    val activeRelay = shared.status.vpnStatus.connected?.relay
    val isSelected = shared.status.tunnelArgs.relay.country?.countryCode == country.countryCode
    val isConnected = activeRelay?.countryCode == country.countryCode

    ObscuraCard(contentPadding = PaddingValues(0.dp)) {
        CountryHeaderRow(
            header =
                CountryHeaderState(
                    country.countryCode,
                    country.countryName,
                    country.cities.size,
                    badgeFor(isConnected, isSelected),
                    expanded,
                ),
            onSelect = { onSelectRelay(RelaySelector.forCountry(country.countryCode)) },
            onToggle = { shared.expandedCountries[key] = !expanded },
        )
        if (expanded) {
            country.cities.forEach { city ->
                SectionDivider()
                RelayCitySection(city, shared, onSelectRelay)
            }
        }
    }
}

@Composable
private fun RelayCitySection(
    city: RelayCityGroup,
    shared: PickerSharedState,
    onSelectRelay: (RelaySelector) -> Unit,
) {
    val forceExpand = shared.query.isNotBlank()
    val cityKey = "r_${city.countryCode}_${city.cityCode}"
    val expanded = forceExpand || shared.expandedCities[cityKey] == true
    val isCityConnected =
        shared.status.vpnStatus.connected?.relay?.let {
            it.countryCode == city.countryCode && it.cityCode == city.cityCode
        } == true
    val isCitySelected =
        shared.status.tunnelArgs.relay.city?.let {
            it.countryCode == city.countryCode && it.cityCode == city.cityCode
        } == true

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable { onSelectRelay(RelaySelector.forCity(city.countryCode, city.cityCode)) }
                .padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(city.cityName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${city.relays.size} ${if (city.relays.size == 1) "relay" else "relays"}",
                style = MaterialTheme.typography.bodySmall,
                color = LocalExtraColors.current.muted,
            )
        }
        if (isCityConnected || isCitySelected) {
            ActiveBadge(connected = isCityConnected)
        }
        IconButton(onClick = { shared.expandedCities[cityKey] = !expanded }) {
            Icon(
                if (expanded) AppIcons.ChevronDown else AppIcons.ChevronRight,
                contentDescription = "Relays",
                modifier = Modifier.size(18.dp),
            )
        }
    }
    if (expanded) {
        city.relays.forEach { relay ->
            val connectedHere = shared.status.vpnStatus.connected?.relay?.id == relay.id
            val selectedHere = shared.status.tunnelArgs.relay.relay?.id == relay.id
            ServerItemRow(
                title = relay.id,
                subtitle = relay.ipV4.ifBlank { null },
                badge = badgeFor(connectedHere, selectedHere),
                onClick = { onSelectRelay(RelaySelector.forRelay(relay.id)) },
            )
        }
    }
}

@Composable
private fun FastestLocationCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ObscuraCard(
        modifier = Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                AppIcons.Speedometer,
                contentDescription = null,
                tint = ObscuraPalette.Orange,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.muted)
            }
            if (selected) {
                Icon(
                    AppIcons.Check,
                    contentDescription = "Selected",
                    tint = ObscuraPalette.Orange,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PinnedCityRow(
    pin: PinnedLocation,
    exits: List<OneExit>,
    selected: Boolean,
    onSelect: () -> Unit,
    onUnpin: () -> Unit,
) {
    val cityName =
        exits.firstOrNull { it.countryCode == pin.countryCode && it.cityCode == pin.cityCode }?.cityName ?: pin.cityCode
    ObscuraCard(
        modifier = Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onSelect),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(AccountIdUtils.countryFlagEmoji(pin.countryCode), fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(cityName, style = MaterialTheme.typography.titleMedium)
                Text(
                    AccountIdUtils.countryDisplayName(pin.countryCode),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalExtraColors.current.muted,
                )
            }
            if (selected) {
                Icon(
                    AppIcons.Check,
                    contentDescription = null,
                    tint = ObscuraPalette.Orange,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onUnpin) {
                Icon(
                    AppIcons.Pin,
                    contentDescription = "Unpin",
                    tint = ObscuraPalette.Orange,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ServerItemRow(
    title: String,
    subtitle: String?,
    badge: RowStatusBadge,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 36.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalExtraColors.current.muted)
            }
        }
        if (badge != RowStatusBadge.NONE) {
            ActiveBadge(connected = badge == RowStatusBadge.CONNECTED)
        }
    }
}

@Composable
private fun ActiveBadge(connected: Boolean) {
    val color = if (connected) LocalExtraColors.current.success else ObscuraPalette.Orange
    Text(
        text = if (connected) "Connected" else "Selected",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalExtraColors.current.muted,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp, start = 2.dp),
    )
}

@Composable
private fun LoadingBox(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(color = ObscuraPalette.Orange)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = LocalExtraColors.current.muted)
        }
    }
}

@Composable
private fun ErrorRetryBox(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NoticeBanner(text = message, kind = NoticeKind.ERROR)
            TextButton(onClick = onRetry) { Text("Retry", color = ObscuraPalette.Orange) }
        }
    }
}

private fun buildExitTree(exits: List<OneExit>, query: String): List<ExitCountryGroup> {
    val q = query.trim().lowercase()
    val filtered =
        if (q.isEmpty()) exits
        else {
            exits.filter { exit ->
                exit.cityName.lowercase().contains(q) ||
                    exit.id.lowercase().contains(q) ||
                    exit.providerName.lowercase().contains(q) ||
                    AccountIdUtils.countryDisplayName(exit.countryCode).lowercase().contains(q)
            }
        }
    return filtered
        .groupBy { it.countryCode }
        .map { (countryCode, countryExits) ->
            val cities =
                countryExits
                    .groupBy { it.cityCode }
                    .map { (cityCode, list) ->
                        ExitCityGroup(countryCode, cityCode, list.first().cityName, list.sortedBy { it.id })
                    }
                    .sortedBy { it.cityName }
            ExitCountryGroup(countryCode, AccountIdUtils.countryDisplayName(countryCode), cities)
        }
        .sortedBy { it.countryName }
}

private fun buildRelayTree(relays: List<OneRelay>, query: String): List<RelayCountryGroup> {
    val q = query.trim().lowercase()
    val filtered =
        if (q.isEmpty()) relays
        else {
            relays.filter { relay ->
                relay.cityName.lowercase().contains(q) ||
                    relay.id.lowercase().contains(q) ||
                    AccountIdUtils.countryDisplayName(relay.countryCode).lowercase().contains(q)
            }
        }
    return filtered
        .groupBy { it.countryCode }
        .map { (countryCode, countryRelays) ->
            val cities =
                countryRelays
                    .groupBy { it.cityCode }
                    .map { (cityCode, list) ->
                        RelayCityGroup(countryCode, cityCode, list.first().cityName, list.sortedBy { it.id })
                    }
                    .sortedBy { it.cityName }
            RelayCountryGroup(countryCode, AccountIdUtils.countryDisplayName(countryCode), cities)
        }
        .sortedBy { it.countryName }
}

private fun filterPinned(
    pinned: List<PinnedLocation>,
    exits: List<OneExit>,
    query: String,
): List<PinnedLocation> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return pinned
    return pinned.filter { pin ->
        val city =
            exits.firstOrNull { it.countryCode == pin.countryCode && it.cityCode == pin.cityCode }?.cityName.orEmpty()
        city.lowercase().contains(q) || AccountIdUtils.countryDisplayName(pin.countryCode).lowercase().contains(q)
    }
}

private fun togglePinnedList(
    current: List<PinnedLocation>,
    countryCode: String,
    cityCode: String,
): List<PinnedLocation> {
    val exists = current.any { it.countryCode == countryCode && it.cityCode == cityCode }
    return if (exists) {
        current.filterNot { it.countryCode == countryCode && it.cityCode == cityCode }
    } else {
        current + PinnedLocation(countryCode, cityCode, System.currentTimeMillis() / 1000L)
    }
}
