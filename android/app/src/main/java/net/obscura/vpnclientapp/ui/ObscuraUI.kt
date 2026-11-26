package net.obscura.vpnclientapp.ui

import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import net.obscura.vpnclientapp.activities.MainActivity
import net.obscura.vpnclientapp.services.IObscuraVpnService
import net.obscura.vpnclientapp.ui.compose.ObscuraRoot

class ObscuraUI {
    private val repoFlow = MutableStateFlow<CoreRepository?>(null)
    private val startErrorFlow = MutableStateFlow<String?>(null)
    private var contentAttached = false

    fun attachContent(
        activity: MainActivity,
        osStatusManager: OsStatusManager,
        preferencesManager: PreferencesManager,
    ) {
        if (contentAttached) return
        contentAttached = true
        activity.setContent {
            val repo by repoFlow.collectAsState()
            val startError by startErrorFlow.collectAsState()
            ObscuraRoot(
                repo = repo,
                osStatusManager = osStatusManager,
                preferencesManager = preferencesManager,
                startError = startError,
            )
        }
    }

    fun onCreate(
        isFreshLaunch: Boolean,
        service: IObscuraVpnService,
        activity: MainActivity,
        osStatusManager: OsStatusManager,
        preferencesManager: PreferencesManager,
    ) {
        attachContent(activity, osStatusManager, preferencesManager)
        repoFlow.value?.stop()
        val repository = CoreRepository(service, activity, osStatusManager, isFreshLaunch)
        repository.start(activity)
        repoFlow.value = repository
    }

    fun handleObscuraUri(uri: Uri, osStatusManager: OsStatusManager) {
        val host = uri.host ?: return
        val target =
            when (host.lowercase()) {
                "location",
                "locations" -> OsStatus.NavigationView.Location
                "account" -> OsStatus.NavigationView.Account
                "settings" -> OsStatus.NavigationView.Settings
                "about" -> OsStatus.NavigationView.About
                else -> OsStatus.NavigationView.Connection
            }
        osStatusManager.update { this.navigationView = target }
    }

    fun onResume() = Unit

    fun onPause() = Unit

    fun invalidate() = Unit

    fun onDestroy() {
        repoFlow.value?.stop()
        repoFlow.value = null
    }
}
