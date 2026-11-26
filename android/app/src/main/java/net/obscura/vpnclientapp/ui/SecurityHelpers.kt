package net.obscura.vpnclientapp.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun rememberScreenLockGate(): (onUnlocked: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val action = pendingAction
            pendingAction = null
            if (result.resultCode == Activity.RESULT_OK) {
                action?.invoke()
            }
        }
    return { onUnlocked ->
        val km = context.getSystemService(KeyguardManager::class.java)
        if (!km.isDeviceSecure) {
            onUnlocked()
        } else {
            @Suppress("DEPRECATION")
            val intent = km.createConfirmDeviceCredentialIntent("Obscura VPN", "Confirm with your screen lock")
            if (intent == null) {
                onUnlocked()
            } else {
                pendingAction = onUnlocked
                launcher.launch(intent)
            }
        }
    }
}

@Composable
fun SecureWindowEffect(secure: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, secure) {
        if (secure) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (secure) {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

class SensitiveClipboard(private val context: Context, private val scope: CoroutineScope) {
    private var clearJob: Job? = null

    fun copyAccountNumber(accountNumber: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip =
            ClipData.newPlainText("Obscura account number", accountNumber).apply {
                description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            }
        clipboard.setPrimaryClip(clip)
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(CLEAR_DELAY_MS)
            val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (current == accountNumber) {
                clipboard.clearPrimaryClip()
            }
        }
    }

    private companion object {
        const val CLEAR_DELAY_MS = 60_000L
    }
}
