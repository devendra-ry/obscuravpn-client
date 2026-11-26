package net.obscura.vpnclientapp.services

import java.io.File
import kotlinx.serialization.Serializable
import net.obscura.lib.util.Logger
import net.obscura.vpnclientapp.client.jsonConfig

private val log = Logger("AndroidVpnSettings")

@Serializable
enum class SplitMode {
    Off,
    Exclude,
    Include,
}

@Serializable
data class AndroidVpnSettings(
    val customDns: List<String> = emptyList(),
    val splitMode: SplitMode = SplitMode.Off,
    val splitApps: Set<String> = emptySet(),
    val excludedCidrs: List<String> = emptyList(),
    val persistLogs: Boolean = false,
)

class AndroidVpnSettingsStore(dir: File) {
    private val file = File(dir, "android_settings.json")

    @Synchronized
    fun load(): AndroidVpnSettings {
        if (!file.exists()) return AndroidVpnSettings()
        return runCatching { jsonConfig.decodeFromString<AndroidVpnSettings>(file.readText()) }
            .onFailure { log.warn("failed to load android settings: ${it.message}") }
            .getOrDefault(AndroidVpnSettings())
    }

    @Synchronized
    fun save(settings: AndroidVpnSettings) {
        runCatching {
                val tmp = File(file.parentFile, "android_settings.json.tmp")
                tmp.writeText(jsonConfig.encodeToString(settings))
                if (!tmp.renameTo(file)) {
                    file.writeText(jsonConfig.encodeToString(settings))
                    tmp.delete()
                }
            }
            .onFailure { log.error("failed to save android settings: ${it.message}") }
    }
}
