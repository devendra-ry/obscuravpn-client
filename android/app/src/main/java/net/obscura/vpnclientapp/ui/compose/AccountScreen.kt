package net.obscura.vpnclientapp.ui.compose

import android.content.Intent
import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch
import net.obscura.vpnclientapp.R
import net.obscura.vpnclientapp.client.AccountIdUtils
import net.obscura.vpnclientapp.client.AccountInfo
import net.obscura.vpnclientapp.client.CoreStatus
import net.obscura.vpnclientapp.ui.CoreRepository
import net.obscura.vpnclientapp.ui.SecureWindowEffect
import net.obscura.vpnclientapp.ui.SensitiveClipboard
import net.obscura.vpnclientapp.ui.rememberScreenLockGate

private data class BillingCardState(
    val accountId: String,
    val account: AccountInfo?,
    val playBilling: Boolean,
    val busy: Boolean,
    val errorMsg: String?,
)

@Composable
fun AccountScreen(
    repo: CoreRepository,
    status: CoreStatus,
    playBilling: Boolean,
    contentPadding: PaddingValues,
) {
    SecureWindowEffect(true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var billingError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val billingState =
        BillingCardState(status.accountId.orEmpty(), status.account?.accountInfo, playBilling, busy, billingError)

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Account", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        AccountStatusCard(account = status.account?.accountInfo)
        BillingCard(
            state = billingState,
            onRefresh = {
                scope.launch {
                    busy = true
                    repo.refreshAccountInfo()
                    busy = false
                }
            },
            onPurchasePlay = { promo ->
                scope.launch {
                    busy = true
                    billingError = null
                    runCatching { repo.purchasePlaySubscription(promo.ifBlank { null }) }
                        .onFailure { billingError = it.message ?: "Purchase failed" }
                    busy = false
                }
            },
            onOpenWebBilling = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
        )
        AccountNumberCard(accountId = status.accountId.orEmpty())
        AccountActionsCard(
            onLogout = { scope.launch { repo.logout() } },
            onDeleteRequest = { showDeleteConfirm = true },
        )
    }

    if (showDeleteConfirm) {
        DeleteAccountDialog(
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                scope.launch { repo.deleteAccount() }
            },
        )
    }
}

@Composable
private fun AccountStatusCard(account: AccountInfo?) {
    val badgeInfo = resolveBadge(account)
    ObscuraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(badgeInfo.drawableRes), contentDescription = null, modifier = Modifier.size(36.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(badgeInfo.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    badgeInfo.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalExtraColors.current.muted,
                )
            }
        }
    }
}

@Composable
private fun BillingCard(
    state: BillingCardState,
    onRefresh: () -> Unit,
    onPurchasePlay: (String) -> Unit,
    onOpenWebBilling: (String) -> Unit,
) {
    var promoCode by remember { mutableStateOf("") }
    ObscuraCard {
        Text("Subscription & Time", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (state.playBilling) {
            val hasGoogleSub = state.account?.googleSubscription?.active == true
            if (!hasGoogleSub) {
                OutlinedTextField(
                    value = promoCode,
                    onValueChange = { promoCode = it },
                    label = { Text("Promo code (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                ObscuraGradientButton(
                    text = if (state.busy) "Working…" else "Top Up / Subscribe",
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onPurchasePlay(promoCode.trim()) },
                )
            } else {
                OutlinedButton(
                    onClick = { onOpenWebBilling("https://play.google.com/store/account/subscriptions") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Manage Google Play Subscription")
                    Spacer(Modifier.width(6.dp))
                    Icon(AppIcons.ExternalLink, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            ObscuraGradientButton(
                text = "Top Up Account",
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenWebBilling("https://obscura.com/pay#account_id=${state.accountId}") },
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRefresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Refreshing…" else "Refresh Account Status", color = ObscuraPalette.Orange)
        }
        state.errorMsg?.let { NoticeBanner(text = it, kind = NoticeKind.ERROR) }
    }
}

@Composable
private fun AccountNumberCard(accountId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = remember(context, scope) { SensitiveClipboard(context, scope) }
    val requestUnlock = rememberScreenLockGate()
    var revealed by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    ObscuraCard {
        Text("Account Number", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        val display = if (revealed) AccountIdUtils.formatGrouped(accountId) else "•••• - •••• - •••• - •••• - ••••"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                display,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontMono),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { if (revealed) revealed = false else requestUnlock { revealed = true } }) {
                Icon(
                    if (revealed) AppIcons.EyeOff else AppIcons.Eye,
                    contentDescription = if (revealed) "Hide" else "Reveal",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = {
                    requestUnlock {
                        clipboard.copyAccountNumber(accountId)
                        copied = true
                    }
                },
            ) {
                Icon(AppIcons.Copy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
            }
        }
        Text(
            if (copied) "Copied to clipboard (auto-clears in 60s)"
            else "Keep this number safe. It is your only credential.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalExtraColors.current.muted,
        )
    }
}

@Composable
private fun AccountActionsCard(onLogout: () -> Unit, onDeleteRequest: () -> Unit) {
    ObscuraCard {
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Log Out") }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onDeleteRequest,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Delete Account")
        }
    }
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Account?") },
        text = {
            Text(
                "This permanently deletes your Obscura account number and any remaining time on it. " +
                    "This cannot be undone.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                    ),
            ) {
                Text("Delete permanently")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private data class AccountBadgeUi(val drawableRes: Int, val title: String, val subtitle: String)

private fun resolveBadge(account: AccountInfo?): AccountBadgeUi {
    if (account == null) {
        return AccountBadgeUi(R.drawable.badge_account_expired, "Checking status…", "Loading account details")
    }
    if (!account.active) {
        return AccountBadgeUi(R.drawable.badge_account_expired, "Account Inactive", "Top up or subscribe to connect")
    }
    val sub = account.googleSubscription ?: account.appleSubscription ?: account.stripeSubscription
    if (sub?.active == true) {
        val canceled = sub.cancelAtPeriodEnd == true || sub.autoRenewStatus == false
        val expiry = sub.expiresAt ?: sub.currentPeriodEnd ?: account.currentExpiry
        val dateText = expiry?.let(::formatEpochSeconds) ?: ""
        return if (canceled) {
            val subTitle = if (dateText.isNotEmpty()) "Ends on $dateText" else "Active"
            AccountBadgeUi(R.drawable.badge_subscription_paused, "Subscription Canceled", subTitle)
        } else {
            val subTitle = if (dateText.isNotEmpty()) "Renews on $dateText" else "Active"
            AccountBadgeUi(R.drawable.badge_subscription_active, "Active Subscription", subTitle)
        }
    }
    if (account.freeTrial?.active == true) {
        return AccountBadgeUi(
            R.drawable.badge_paid_up,
            "Free Trial",
            "Expires on ${formatEpochSeconds(account.freeTrial.expiresAt)}",
        )
    }
    val expiry = account.currentExpiry ?: account.topUp?.creditExpiresAt
    return AccountBadgeUi(
        R.drawable.badge_paid_up,
        "Account Active",
        if (expiry != null) "Expires on ${formatEpochSeconds(expiry)}" else "Active",
    )
}

private fun formatEpochSeconds(secs: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(secs))
