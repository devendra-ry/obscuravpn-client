package net.obscura.vpnclientapp.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.obscura.vpnclientapp.client.AccountIdUtils
import net.obscura.vpnclientapp.client.CoreStatus
import net.obscura.vpnclientapp.client.ExitSelector
import net.obscura.vpnclientapp.client.OneExit
import net.obscura.vpnclientapp.client.OneRelay
import net.obscura.vpnclientapp.client.PinnedLocation
import net.obscura.vpnclientapp.client.RelaySelector
import net.obscura.vpnclientapp.client.TrafficStats
import net.obscura.vpnclientapp.ui.CoreRepository
import net.obscura.vpnclientapp.ui.ListLoadState

enum class LocationTabMode {
    EXIT,
    ENTRY,
}

data class LocationScreenState(
    val status: CoreStatus,
    val exits: ListLoadState<OneExit>,
    val relays: ListLoadState<OneRelay>,
    val internetAvailable: Boolean,
    val mode: LocationTabMode,
    val stats: TrafficStats? = null,
)

/** A city with its servers (exits or entry relays), and a country with its cities, as in Ombra's picker. */
private data class City<S>(val countryCode: String, val cityCode: String, val name: String, val servers: List<S>)

private data class Country<S>(val code: String, val name: String, val cities: List<City<S>>) {
    val serverCount: Int
        get() = cities.sumOf { it.servers.size }
}

/** Which countries and cities are folded out; a search opens every country. */
private class Folds(val query: String) {
    val open: SnapshotStateMap<String, Boolean> = mutableStateMapOf()

    fun isOpen(key: String, byQuery: Boolean = true): Boolean = (byQuery && query.isNotBlank()) || open[key] == true

    fun toggle(key: String, byQuery: Boolean = true) {
        open[key] = !isOpen(key, byQuery)
    }
}

private val CITY_INDENT = 64.dp
private val SERVER_INDENT = 80.dp

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
    val folds = remember(query) { Folds(query) }
    val selectExit: (ExitSelector) -> Unit = { selector ->
        scope.launch {
            val saved = repo.updateTunnelArgs(state.status.tunnelArgs.copy(exit = selector))
            if (saved.isSuccess && state.status.vpnStatus.disconnected == null) onNavigateToConnection()
        }
    }
    val selectRelay: (RelaySelector) -> Unit = { selector ->
        scope.launch { repo.updateTunnelArgs(state.status.tunnelArgs.copy(relay = selector)) }
    }
    val togglePin: (String, String) -> Unit = { country, city ->
        scope.launch { repo.setPinnedExits(togglePinnedList(state.status.pinnedLocations, country, city)) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "status") {
            VpnStatusCard(
                state.status.vpnStatus,
                state.stats,
                state.internetAvailable,
                onConnect = {
                    scope.launch {
                        repo.connect()
                        onNavigateToConnection()
                    }
                },
                onDisconnect = { repo.disconnect() },
            )
        }
        item(key = "mode") { LocationModeToggle(state.mode, onModeChange) }
        item(key = "search") { SearchBox(query, state.mode) { query = it } }
        if (state.mode == LocationTabMode.EXIT) {
            exitItems(state, folds, ExitActions(selectExit, togglePin) { scope.launch { repo.refreshExits() } })
        } else {
            relayItems(state, folds, selectRelay) { scope.launch { repo.refreshRelays() } }
        }
    }
}

private class ExitActions(
    val select: (ExitSelector) -> Unit,
    val togglePin: (String, String) -> Unit,
    val retry: () -> Unit,
)

private fun LazyListScope.exitItems(state: LocationScreenState, folds: Folds, actions: ExitActions) {
    val exits = state.exits
    if (exits !is ListLoadState.Ready) {
        item(key = "exits_pending") { ListPending(exits, "Loading exit locations…", actions.retry) }
        return
    }
    val status = state.status
    val countries = groupByCountry(exits.items, folds.query, { it.cityName }, ::exitMatches)
    val pinned = pinnedCities(status.pinnedLocations, countries)
    if (folds.query.isBlank()) {
        item(key = "fastest_exit") {
            FastestCard("Fastest exit", "Automatically picks the quickest server", status.tunnelArgs.exit.any != null) {
                actions.select(ExitSelector.Any)
            }
        }
    }
    if (pinned.isNotEmpty()) {
        item(key = "pinned") {
            Column {
                GroupLabel("Pinned")
                RowGroup {
                    pinned.forEachIndexed { i, city ->
                        if (i > 0) GroupDivider()
                        ExitCityRow(city, status, actions, folds, pinnedSection = true)
                    }
                }
            }
        }
    }
    item(key = "all_exits") {
        Column {
            GroupLabel("All locations")
            if (countries.isEmpty()) NoMatches() else ExitCountryGroup(countries, status, folds, actions)
        }
    }
}

@Composable
private fun ExitCountryGroup(
    countries: List<Country<OneExit>>,
    status: CoreStatus,
    folds: Folds,
    actions: ExitActions,
) {
    val chosen = status.tunnelArgs.exit
    val connectedExit = status.vpnStatus.connected?.exit
    RowGroup {
        countries.forEachIndexed { i, country ->
            if (i > 0) GroupDivider()
            CountryRow(
                country,
                CountryRowState(
                    selected = chosen.country?.countryCode == country.code,
                    connected = connectedExit?.countryCode == country.code,
                    open = folds.isOpen(country.code),
                    serverNoun = "server",
                ),
                onSelect = { actions.select(ExitSelector.forCountry(country.code)) },
                onToggle = { folds.toggle(country.code) },
            )
            if (folds.isOpen(country.code)) {
                country.cities.forEach { city ->
                    GroupDivider(CITY_INDENT)
                    ExitCityRow(city, status, actions, folds, pinnedSection = false)
                }
            }
        }
    }
}

@Composable
private fun ExitCityRow(
    city: City<OneExit>,
    status: CoreStatus,
    actions: ExitActions,
    folds: Folds,
    pinnedSection: Boolean,
) {
    val chosen = status.tunnelArgs.exit
    val connectedExit = status.vpnStatus.connected?.exit
    val key = "exit_${city.countryCode}_${city.cityCode}"
    val open = !pinnedSection && folds.isOpen(key, byQuery = false)
    val isPinned = status.pinnedLocations.any { it.countryCode == city.countryCode && it.cityCode == city.cityCode }
    val servers = count(city.servers.size, "server")
    GroupRow(
        title = city.name,
        supporting = {
            SupportingText(
                if (pinnedSection) "${AccountIdUtils.countryDisplayName(city.countryCode)} · $servers" else servers,
                connected = connectedExit?.countryCode == city.countryCode && connectedExit.cityCode == city.cityCode,
            )
        },
        look =
            if (pinnedSection) RowLook(leading = { FlagBadge(city.countryCode) })
            else RowLook(startPadding = CITY_INDENT),
        onClick = { actions.select(ExitSelector.forCity(city.countryCode, city.cityCode)) },
    ) {
        if (chosen.city?.let { it.countryCode == city.countryCode && it.cityCode == city.cityCode } == true)
            SelectedMark()
        PinButton(isPinned) { actions.togglePin(city.countryCode, city.cityCode) }
        if (!pinnedSection) FoldButton(open, "Servers") { folds.toggle(key, byQuery = false) }
    }
    if (open) {
        city.servers.forEach { exit ->
            GroupDivider(SERVER_INDENT)
            GroupRow(
                title = exit.id,
                supporting = { SupportingText(exitFacts(exit), connected = connectedExit?.id == exit.id) },
                look = RowLook(startPadding = SERVER_INDENT, monoTitle = true),
                onClick = { actions.select(ExitSelector.forExit(exit.id)) },
            ) {
                if (chosen.exit?.id == exit.id) SelectedMark()
            }
        }
    }
}

private fun LazyListScope.relayItems(
    state: LocationScreenState,
    folds: Folds,
    select: (RelaySelector) -> Unit,
    retry: () -> Unit,
) {
    val relays = state.relays
    if (relays !is ListLoadState.Ready) {
        item(key = "relays_pending") { ListPending(relays, "Loading entry relays…", retry) }
        return
    }
    val chosen = state.status.tunnelArgs.relay
    if (folds.query.isBlank()) {
        item(key = "fastest_relay") {
            FastestCard("Fastest entry relay", "Picked automatically for low latency", chosen.any != null) {
                select(RelaySelector.Any)
            }
        }
    }
    val countries = groupByCountry(relays.items, folds.query, { it.cityName }, ::relayMatches)
    item(key = "all_relays") {
        Column {
            GroupLabel("All locations")
            if (countries.isEmpty()) NoMatches() else RelayCountryGroup(countries, state.status, folds, select)
        }
    }
}

@Composable
private fun RelayCountryGroup(
    countries: List<Country<OneRelay>>,
    status: CoreStatus,
    folds: Folds,
    select: (RelaySelector) -> Unit,
) {
    val chosen = status.tunnelArgs.relay
    val connectedRelay = status.vpnStatus.connected?.relay
    RowGroup {
        countries.forEachIndexed { i, country ->
            if (i > 0) GroupDivider()
            val key = "r_${country.code}"
            CountryRow(
                country,
                CountryRowState(
                    selected = chosen.country?.countryCode == country.code,
                    connected = connectedRelay?.countryCode == country.code,
                    open = folds.isOpen(key),
                    serverNoun = "relay",
                ),
                onSelect = { select(RelaySelector.forCountry(country.code)) },
                onToggle = { folds.toggle(key) },
            )
            if (folds.isOpen(key)) {
                country.cities.forEach { city ->
                    GroupDivider(CITY_INDENT)
                    RelayCityRow(city, status, folds, select)
                }
            }
        }
    }
}

@Composable
private fun RelayCityRow(city: City<OneRelay>, status: CoreStatus, folds: Folds, select: (RelaySelector) -> Unit) {
    val chosen = status.tunnelArgs.relay
    val connectedRelay = status.vpnStatus.connected?.relay
    val key = "relay_${city.countryCode}_${city.cityCode}"
    val open = folds.isOpen(key, byQuery = false)
    GroupRow(
        title = city.name,
        supporting = {
            SupportingText(
                count(city.servers.size, "relay"),
                connected = connectedRelay?.countryCode == city.countryCode && connectedRelay.cityCode == city.cityCode,
            )
        },
        look = RowLook(startPadding = CITY_INDENT),
        onClick = { select(RelaySelector.forCity(city.countryCode, city.cityCode)) },
    ) {
        if (chosen.city?.let { it.countryCode == city.countryCode && it.cityCode == city.cityCode } == true)
            SelectedMark()
        FoldButton(open, "Relays") { folds.toggle(key, byQuery = false) }
    }
    if (open) {
        city.servers.forEach { relay ->
            GroupDivider(SERVER_INDENT)
            GroupRow(
                title = relay.id,
                supporting = { SupportingText(relayFacts(relay), connected = connectedRelay?.id == relay.id) },
                look = RowLook(startPadding = SERVER_INDENT, monoTitle = true),
                onClick = { select(RelaySelector.forRelay(relay.id)) },
            ) {
                if (chosen.relay?.id == relay.id) SelectedMark()
            }
        }
    }
}

private data class CountryRowState(
    val selected: Boolean,
    val connected: Boolean,
    val open: Boolean,
    val serverNoun: String,
)

@Composable
private fun <S> CountryRow(country: Country<S>, state: CountryRowState, onSelect: () -> Unit, onToggle: () -> Unit) {
    GroupRow(
        title = country.name,
        look = RowLook(leading = { FlagBadge(country.code) }),
        supporting = {
            SupportingText(
                "${count(country.cities.size, "city", "cities")} · ${count(country.serverCount, state.serverNoun)}",
                connected = state.connected,
            )
        },
        onClick = onSelect,
    ) {
        if (state.selected) SelectedMark()
        FoldButton(state.open, "Cities", onToggle)
    }
}

@Composable
private fun FastestCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    RowGroup {
        GroupRow(
            title = title,
            look =
                RowLook(
                    leading = {
                        Icon(
                            AppIcons.Speedometer,
                            contentDescription = null,
                            tint = ObscuraPalette.Orange,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                ),
            supporting = { SupportingText(subtitle) },
            onClick = onClick,
        ) {
            if (selected) SelectedMark()
        }
    }
}

@Composable
private fun PinButton(pinned: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            AppIcons.Pin,
            contentDescription = if (pinned) "Unpin" else "Pin",
            tint = if (pinned) ObscuraPalette.Orange else LocalExtraColors.current.muted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SearchBox(query: String, mode: LocationTabMode, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(if (mode == LocationTabMode.EXIT) "Search country, city or server" else "Search entry relays")
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
        shape = RoundedCornerShape(50),
        colors =
            OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = LocalExtraColors.current.border,
                focusedBorderColor = ObscuraPalette.Orange,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LocationModeToggle(mode: LocationTabMode, onModeChange: (LocationTabMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(LocationTabMode.EXIT to "Exit", LocationTabMode.ENTRY to "Entry relay").forEachIndexed { i, (m, label) ->
            SegmentedButton(
                selected = mode == m,
                onClick = { onModeChange(m) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun ListPending(state: ListLoadState<*>, loading: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state is ListLoadState.Error) {
                NoticeBanner(text = "Could not load the list (${state.code})", kind = NoticeKind.ERROR)
                TextButton(onClick = onRetry) { Text("Retry", color = ObscuraPalette.Orange) }
            } else {
                CircularProgressIndicator(color = ObscuraPalette.Orange)
                Text(loading, style = MaterialTheme.typography.bodyMedium, color = LocalExtraColors.current.muted)
            }
        }
    }
}

@Composable
private fun NoMatches() {
    Text(
        "No locations found",
        style = MaterialTheme.typography.bodyMedium,
        color = LocalExtraColors.current.muted,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
    )
}

private fun count(n: Int, one: String, many: String = "${one}s"): String = "$n ${if (n == 1) one else many}"

private fun exitMatches(exit: OneExit, q: String): Boolean =
    exit.cityName.lowercase().contains(q) ||
        exit.id.lowercase().contains(q) ||
        exit.providerName.lowercase().contains(q) ||
        AccountIdUtils.countryDisplayName(exit.countryCode).lowercase().contains(q)

private fun relayMatches(relay: OneRelay, q: String): Boolean =
    relay.cityName.lowercase().contains(q) ||
        relay.id.lowercase().contains(q) ||
        relay.ipV4.contains(q) ||
        AccountIdUtils.countryDisplayName(relay.countryCode).lowercase().contains(q)

/** Countries by name, their cities by name, servers by id with numbers in order (wg-2 before wg-10). */
private fun <S> groupByCountry(
    items: List<S>,
    query: String,
    cityName: (S) -> String,
    matches: (S, String) -> Boolean,
): List<Country<S>> {
    val q = query.trim().lowercase()
    val shown = if (q.isEmpty()) items else items.filter { matches(it, q) }
    return shown
        .groupBy { countryCodeOf(it) }
        .map { (code, inCountry) ->
            val cities =
                inCountry
                    .groupBy { cityCodeOf(it) }
                    .map { (cityCode, servers) ->
                        City(code, cityCode, cityName(servers.first()), servers.sortedWith(compareBy(::numericKey)))
                    }
                    .sortedBy { it.name }
            Country(code, AccountIdUtils.countryDisplayName(code), cities)
        }
        .sortedBy { it.name }
}

private fun countryCodeOf(item: Any?): String =
    when (item) {
        is OneExit -> item.countryCode
        is OneRelay -> item.countryCode
        else -> ""
    }

private fun cityCodeOf(item: Any?): String =
    when (item) {
        is OneExit -> item.cityCode
        is OneRelay -> item.cityCode
        else -> ""
    }

private fun numericKey(item: Any?): String {
    val id =
        when (item) {
            is OneExit -> item.id
            is OneRelay -> item.id
            else -> ""
        }
    return id.replace(Regex("\\d+")) { it.value.padStart(6, '0') }
}

private fun pinnedCities(pins: List<PinnedLocation>, countries: List<Country<OneExit>>): List<City<OneExit>> =
    pins.mapNotNull { pin ->
        countries.firstOrNull { it.code == pin.countryCode }?.cities?.firstOrNull { it.cityCode == pin.cityCode }
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
