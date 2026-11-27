package net.obscura.vpnclientapp.client

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoreStatus(
    val version: String,
    val vpnStatus: CoreVpnStatus,
    val accountId: String? = null,
    val inNewAccountFlow: Boolean = false,
    val pinnedLocations: List<PinnedLocation> = emptyList(),
    val lastChosenExit: ExitSelector = ExitSelector.Any,
    val lastExit: ExitSelector = ExitSelector.Any,
    val apiUrl: String = "",
    val account: AccountStatus? = null,
    val autoConnect: Boolean = false,
    val featureFlags: FeatureFlags = FeatureFlags(),
    val featureFlagKeys: List<String> = emptyList(),
    val useSystemDns: Boolean = false,
    val localNetworkAccess: Boolean = false,
    val dnsContentBlock: DnsContentBlock = DnsContentBlock(),
    val tunnelArgs: TunnelArgs = TunnelArgs(),
    val wgKeyMaxAgeSecs: Long = 30L * 24L * 3600L,
)

@Serializable
data class CoreVpnStatus(
    val connected: ConnectedInfo? = null,
    val connecting: ConnectingInfo? = null,
    val disconnected: kotlinx.serialization.json.JsonObject? = null,
) {
    @Serializable
    data class ConnectedInfo(
        val exit: OneExit,
        val relay: OneRelay? = null,
        val clientPublicKey: String = "",
        val exitPublicKey: String = "",
        val transport: TunnelMode = TunnelMode.QUIC,
        val tunnelArgs: TunnelArgs = TunnelArgs(),
    )

    @Serializable
    data class ConnectingInfo(
        val connectError: String? = null,
        val reconnecting: Boolean = false,
        val tunnelArgs: TunnelArgs = TunnelArgs(),
    )
}

@Serializable
enum class TunnelMode {
    @SerialName("quic") QUIC,
    @SerialName("wireguardUdp") WIREGUARD_UDP,
    @SerialName("tcpTls") TCP_TLS,
}

@Serializable
data class TunnelArgs(
    val exit: ExitSelector = ExitSelector.Any,
    val relay: RelaySelector = RelaySelector.Any,
    val mode: TunnelMode = TunnelMode.QUIC,
    val udpMtu: Int? = null,
)

@Serializable
data class CityCode(
    @SerialName("country_code") val countryCode: String,
    @SerialName("city_code") val cityCode: String,
)

@Serializable
data class ExitIdRef(
    val id: String,
)

@Serializable
data class CountryRef(
    @SerialName("country_code") val countryCode: String,
)

/**
 * A Rust enum, externally tagged: exactly one field is set and only that one may be sent, since `jsonConfig` writes
 * defaults and Rust rejects `{"any":{},"exit":null,...}`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ExitSelector(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val any: kotlinx.serialization.json.JsonObject? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val exit: ExitIdRef? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val country: CountryRef? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val city: CityCode? = null,
) {
    companion object {
        val Any = ExitSelector(any = kotlinx.serialization.json.JsonObject(emptyMap()))

        fun forExit(id: String) = ExitSelector(exit = ExitIdRef(id))

        fun forCountry(countryCode: String) = ExitSelector(country = CountryRef(countryCode))

        fun forCity(countryCode: String, cityCode: String) = ExitSelector(city = CityCode(countryCode, cityCode))
    }
}

/**
 * A Rust enum, externally tagged: exactly one field is set and only that one may be sent, since `jsonConfig` writes
 * defaults and Rust rejects `{"any":{},"relay":null,...}`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RelaySelector(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val any: kotlinx.serialization.json.JsonObject? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val relay: ExitIdRef? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val country: CountryRef? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val city: CityCode? = null,
) {
    companion object {
        val Any = RelaySelector(any = kotlinx.serialization.json.JsonObject(emptyMap()))

        fun forRelay(id: String) = RelaySelector(relay = ExitIdRef(id))

        fun forCountry(countryCode: String) = RelaySelector(country = CountryRef(countryCode))

        fun forCity(countryCode: String, cityCode: String) = RelaySelector(city = CityCode(countryCode, cityCode))
    }
}

@Serializable
data class PinnedLocation(
    @SerialName("country_code") val countryCode: String,
    @SerialName("city_code") val cityCode: String,
    @SerialName("pinned_at") val pinnedAt: Long = 0,
)

@Serializable
data class OneExit(
    val id: String,
    @SerialName("country_code") val countryCode: String,
    @SerialName("city_code") val cityCode: String,
    @SerialName("city_name") val cityName: String,
    @SerialName("provider_id") val providerId: String = "",
    @SerialName("provider_url") val providerUrl: String = "",
    @SerialName("provider_name") val providerName: String = "",
    @SerialName("provider_homepage_url") val providerHomepageUrl: String = "",
    @SerialName("datacenter_id") val datacenterId: Long? = null,
    val tier: Int? = null,
)

@Serializable
data class OneRelay(
    val id: String,
    @SerialName("country_code") val countryCode: String,
    @SerialName("city_code") val cityCode: String,
    @SerialName("city_name") val cityName: String,
    @SerialName("ip_v4") val ipV4: String = "",
    val ports: List<Int> = emptyList(),
    /** The exits this relay reaches best; the relay picker shows them, the exit picker shows the reverse. */
    @SerialName("preferred_exits") val preferredExits: List<ExitIdRef> = emptyList(),
)

/** Rust's `CachedValue<ExitList>`: unlike the relay list, the exits sit in an object under `exits`. */
@Serializable
data class CachedExitList(
    val version: String,
    @SerialName("last_updated") val lastUpdated: Long = 0,
    val value: ExitList = ExitList(),
)

@Serializable
data class ExitList(
    val exits: List<OneExit> = emptyList(),
)

@Serializable
data class CachedList<T>(
    val version: String,
    @SerialName("last_updated") val lastUpdated: Long = 0,
    val value: List<T> = emptyList(),
)

@Serializable
data class DnsContentBlock(
    val ad: Boolean = false,
    val tracker: Boolean = false,
    val malware: Boolean = false,
    val adult: Boolean = false,
    val gambling: Boolean = false,
    val socialMedia: Boolean = false,
)

@Serializable
data class FeatureFlags(
    val quicFramePadding: Boolean? = null,
    val killSwitch: Boolean? = null,
    val forceSmallMtu: Boolean? = null,
    val tcpTlsTunnel: Boolean? = null,
)

@Serializable
data class AccountStatus(
    @SerialName("account_info") val accountInfo: AccountInfo,
    @SerialName("last_updated_sec") val lastUpdatedSec: Long = 0,
)

@Serializable
data class AccountInfo(
    val id: String,
    val active: Boolean = false,
    @SerialName("top_up") val topUp: TopUpInfo? = null,
    @SerialName("apple_subscription") val appleSubscription: SubscriptionInfo? = null,
    @SerialName("google_subscription") val googleSubscription: SubscriptionInfo? = null,
    @SerialName("stripe_subscription") val stripeSubscription: SubscriptionInfo? = null,
    @SerialName("auto_renews") val autoRenews: Long? = null,
    @SerialName("current_expiry") val currentExpiry: Long? = null,
    @SerialName("free_trial") val freeTrial: FreeTrialInfo? = null,
) {
    @Serializable
    data class TopUpInfo(
        val active: Boolean = false,
        @SerialName("credit_expires_at") val creditExpiresAt: Long = 0,
    )

    @Serializable
    data class SubscriptionInfo(
        val active: Boolean = false,
        @SerialName("auto_renew_status") val autoRenewStatus: Boolean? = null,
        @SerialName("cancel_at_period_end") val cancelAtPeriodEnd: Boolean? = null,
        @SerialName("expires_at") val expiresAt: Long? = null,
        @SerialName("renewal_date") val renewalDate: Long? = null,
        @SerialName("current_period_end") val currentPeriodEnd: Long? = null,
    )

    @Serializable
    data class FreeTrialInfo(
        val active: Boolean = false,
        @SerialName("expires_at") val expiresAt: Long = 0,
    )
}

@Serializable
data class TrafficStats(
    val connectedMs: Long = 0,
    val connId: String = "",
    val txBytes: Long = 0,
    val rxBytes: Long = 0,
    val latestLatencyMs: Double = 0.0,
)

@Serializable
data class ServiceStatusOutput(
    @SerialName("free_trial") val freeTrial: FreeTrialStatus = FreeTrialStatus(),
) {
    @Serializable
    data class FreeTrialStatus(
        val available: Boolean = false,
    )
}
