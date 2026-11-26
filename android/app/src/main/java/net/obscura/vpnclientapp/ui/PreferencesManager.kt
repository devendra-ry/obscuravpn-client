package net.obscura.vpnclientapp.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.obscura.lib.util.Logger
import net.obscura.vpnclientapp.client.jsonConfig

private val log = Logger(PreferencesManager::class)

class PreferencesManager(context: Context, private val onPreferencesChanged: (Preferences) -> Unit) {
    data class Preferences(
        val colorScheme: OsStatus.ColorScheme = OsStatus.ColorScheme.Auto,
        val secureAllScreens: Boolean = false,
    ) {
        companion object {
            private const val KEY_COLOR_SCHEME = "color-scheme"
            private const val KEY_SECURE_ALL = "secure-all-screens"

            internal fun decode(sharedPreferences: SharedPreferences?): Preferences {
                val colorScheme =
                    runCatching {
                        sharedPreferences?.getString(KEY_COLOR_SCHEME, null)?.let {
                            jsonConfig.decodeFromString<OsStatus.ColorScheme>(it)
                        }
                    }
                        .onFailure { log.error("failed to read $KEY_COLOR_SCHEME: ${it.message}") }
                        .getOrNull() ?: OsStatus.ColorScheme.Auto
                val secureAll = sharedPreferences?.getBoolean(KEY_SECURE_ALL, false) ?: false
                return Preferences(colorScheme, secureAll)
            }
        }

        internal fun encode(editor: SharedPreferences.Editor) {
            editor.putString(KEY_COLOR_SCHEME, jsonConfig.encodeToString(this.colorScheme))
            editor.putBoolean(KEY_SECURE_ALL, this.secureAllScreens)
        }
    }

    private val sharedPreferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    private val sharedPreferencesEditorLock = Mutex()
    private val _state = MutableStateFlow(Preferences.decode(sharedPreferences))
    val state: StateFlow<Preferences> = _state.asStateFlow()

    init {
        this.onPreferencesChanged(_state.value)
    }

    suspend fun write(preferences: Preferences) {
        this.sharedPreferencesEditorLock.withLock {
            withContext(Dispatchers.IO) {
                this@PreferencesManager.sharedPreferences.edit(commit = true) { preferences.encode(this) }
            }
            _state.value = preferences
            this.onPreferencesChanged(preferences)
        }
    }
}
