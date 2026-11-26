package net.obscura.vpnclientapp.ui.compose

import android.content.Intent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import net.obscura.vpnclientapp.BuildConfig
import net.obscura.vpnclientapp.R
import net.obscura.vpnclientapp.ui.CoreRepository
import net.obscura.vpnclientapp.ui.launchShareDebugBundle

private data class DebugBundleUiState(
    val feedback: String,
    val inProgress: Boolean,
    val latestPath: String?,
    val errorMsg: String?,
)

@Composable
fun AboutScreen(
    repo: CoreRepository,
    debugInProgress: Boolean,
    latestDebugPath: String?,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf("") }
    var bundleError by remember { mutableStateOf<String?>(null) }
    var showLicenses by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppBrandHeader()
        DebugBundleCard(
            state = DebugBundleUiState(feedback, debugInProgress, latestDebugPath, bundleError),
            onFeedbackChange = { feedback = it },
            onCreateBundle = { asEmail ->
                scope.launch {
                    bundleError = null
                    runCatching { repo.createDebugBundle(feedback.ifBlank { null }) }
                        .onSuccess { path -> launchShareDebugBundle(context, path, asEmail = asEmail) }
                        .onFailure { bundleError = it.message ?: "Failed to create debug bundle" }
                }
            },
        )
        LinksAndLicensesCard(
            onOpenLicenses = { showLicenses = true },
            onOpenUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
        )
        Text(
            "© 2026 Obscura Labs. Privacy-first VPN.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalExtraColors.current.muted,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp),
        )
    }

    if (showLicenses) {
        LicensesSheet(onDismiss = { showLicenses = false })
    }
}

@Composable
private fun AppBrandHeader() {
    ObscuraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(R.drawable.ic_launcher), contentDescription = null, modifier = Modifier.size(52.dp))
            Column {
                Image(
                    painter = painterResource(R.drawable.obscura_wordmark),
                    contentDescription = "Obscura VPN",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.height(22.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalExtraColors.current.muted,
                )
            }
        }
    }
}

@Composable
private fun DebugBundleCard(
    state: DebugBundleUiState,
    onFeedbackChange: (String) -> Unit,
    onCreateBundle: (asEmail: Boolean) -> Unit,
) {
    ObscuraCard {
        Text("Support & Diagnostics", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Create an encrypted diagnostic bundle containing logs and system state to share with support.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalExtraColors.current.muted,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.feedback,
            onValueChange = onFeedbackChange,
            label = { Text("Describe your issue (optional)") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onCreateBundle(false) },
                enabled = !state.inProgress,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.inProgress) "Creating…" else "Share Bundle")
            }
            OutlinedButton(
                onClick = { onCreateBundle(true) },
                enabled = !state.inProgress,
                modifier = Modifier.weight(1f),
            ) {
                Text("Email Support")
            }
        }
        if (state.latestPath != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Latest bundle ready to share.",
                style = MaterialTheme.typography.labelSmall,
                color = LocalExtraColors.current.success,
            )
        }
        state.errorMsg?.let {
            Spacer(Modifier.height(6.dp))
            NoticeBanner(text = it, kind = NoticeKind.ERROR)
        }
    }
}

@Composable
private fun LinksAndLicensesCard(onOpenLicenses: () -> Unit, onOpenUrl: (String) -> Unit) {
    ObscuraCard(contentPadding = PaddingValues(0.dp)) {
        AboutRow(
            "Open-source licenses",
            { Icon(AppIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp)) },
            onOpenLicenses,
        )
        SectionDivider()
        AboutRow(
            "Privacy Policy",
            { Icon(AppIcons.ExternalLink, contentDescription = null, modifier = Modifier.size(16.dp)) },
        ) {
            onOpenUrl("https://obscura.com/privacy")
        }
        SectionDivider()
        AboutRow(
            "Terms of Service",
            { Icon(AppIcons.ExternalLink, contentDescription = null, modifier = Modifier.size(16.dp)) },
        ) {
            onOpenUrl("https://obscura.com/terms")
        }
        SectionDivider()
        AboutRow(
            "Obscura Website",
            { Icon(AppIcons.ExternalLink, contentDescription = null, modifier = Modifier.size(16.dp)) },
        ) {
            onOpenUrl("https://obscura.com")
        }
    }
}

@Composable
private fun AboutRow(title: String, trailing: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicensesSheet(onDismiss: () -> Unit) {
    val libraries = remember {
        listOf(
            "AndroidX & Jetpack Compose" to "Apache-2.0",
            "Kotlin & kotlinx.serialization / coroutines" to "Apache-2.0",
            "Obscura Core / Rust standard library" to "Apache-2.0 / MIT",
            "BoringTun (Cloudflare WireGuard®)" to "BSD-3-Clause",
            "Tokio / Quinn / Rustls / Ring" to "MIT / Apache-2.0 / ISC",
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Open-source licenses", style = MaterialTheme.typography.titleLarge)
            libraries.forEach { (name, license) -> KeyValueRow(name, license) }
            Spacer(Modifier.height(12.dp))
        }
    }
}
