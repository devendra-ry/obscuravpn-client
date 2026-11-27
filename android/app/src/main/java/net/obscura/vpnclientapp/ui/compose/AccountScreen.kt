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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

private const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"

/**
 * The account at a glance, then grouped lists as in Settings: the time on the account says each fact once (the badge
 * says what kind of time and its date, the big number how many days), and the sources only when there are several.
 */
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
    var busy by remember { mutableStateOf(false) }
    var billingError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val account = status.account?.accountInfo
    val accountId = status.accountId.orEmpty()
    val withBusy: (suspend () -> Unit) -> Unit = { work ->
        scope.launch {
            busy = true
            work()
            busy = false
        }
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Account", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        AccountHero(account)
        account?.let { TimeSources(it) }
        AddTimeGroup(
            playBilling = playBilling,
            hasGoogleSub = account?.googleSubscription?.active == true,
            busy = busy,
            onPurchasePlay = { promo ->
                billingError = null
                withBusy {
                    runCatching { repo.purchasePlaySubscription(promo.ifBlank { null }) }
                        .onFailure { billingError = it.message ?: "Purchase failed" }
                }
            },
            accountId = accountId,
        )
        billingError?.let { NoticeBanner(text = it, kind = NoticeKind.ERROR) }
        AccountNumberGroup(accountId)
        ManageGroup(
            busy = busy,
            onRefresh = { withBusy { repo.refreshAccountInfo() } },
            onLogout = { scope.launch { repo.logout() } },
            onDelete = { showDeleteConfirm = true },
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
private fun ManageGroup(busy: Boolean, onRefresh: () -> Unit, onLogout: () -> Unit, onDelete: () -> Unit) {
    GroupLabel("Manage")
    RowGroup {
        ActionRow(if (busy) "Refreshing…" else "Refresh account status", onClick = onRefresh)
        GroupDivider()
        ActionRow("Log out", color = MaterialTheme.colorScheme.onSurface, onClick = onLogout)
        GroupDivider()
        ActionRow("Delete account", color = MaterialTheme.colorScheme.error, onClick = onDelete)
    }
}

/** The badge and what it means, and the days left in large type. */
@Composable
private fun AccountHero(account: AccountInfo?) {
    val badge = resolveBadge(account)
    val muted = LocalExtraColors.current.muted
    ObscuraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(badge.drawableRes), contentDescription = null, modifier = Modifier.size(40.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(badge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(badge.subtitle, style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        val end = badge.endSec
        if (end != null) {
            val days = daysLeft(end, Instant.now().epochSecond)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (days == 0L && end > Instant.now().epochSecond) "<1" else days.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ObscuraPalette.Orange,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    (if (days == 1L) "day" else "days") + if (badge.renews) " until renewal" else " left",
                    style = MaterialTheme.typography.bodyLarge,
                    color = muted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}

/** Where the time comes from, when it comes from more than one place: a subscription and top-up credit, say. */
@Composable
private fun TimeSources(account: AccountInfo) {
    val rows = timeRows(account, Instant.now().epochSecond)
    if (rows.size < 2) return
    GroupLabel("Time on your account")
    RowGroup {
        rows.forEachIndexed { i, (label, date) ->
            if (i > 0) GroupDivider()
            GroupRow(title = label) {
                Text(date, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 10.dp))
            }
        }
    }
}

@Composable
private fun AddTimeGroup(
    playBilling: Boolean,
    hasGoogleSub: Boolean,
    busy: Boolean,
    onPurchasePlay: (String) -> Unit,
    accountId: String,
) {
    val context = LocalContext.current
    val onOpenUrl: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, url.replace("{id}", accountId).toUri()))
    }
    var promoCode by remember { mutableStateOf("") }
    GroupLabel("Add time")
    RowGroup {
        when {
            !playBilling ->
                NavRow("Top up", "Pay on obscura.com with card or crypto") {
                    onOpenUrl("https://obscura.com/pay#account_id={id}")
                }
            hasGoogleSub ->
                NavRow("Google Play subscription", "Change or cancel it in Google Play") {
                    onOpenUrl(PLAY_SUBSCRIPTIONS_URL)
                }
            else ->
                GroupBlock {
                    OutlinedTextField(
                        value = promoCode,
                        onValueChange = { promoCode = it },
                        placeholder = { Text("Promo code (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    ObscuraGradientButton(
                        text = if (busy) "Working…" else "Subscribe or top up",
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onPurchasePlay(promoCode.trim()) },
                    )
                }
        }
    }
}

@Composable
private fun AccountNumberGroup(accountId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = remember(context, scope) { SensitiveClipboard(context, scope) }
    val requestUnlock = rememberScreenLockGate()
    var revealed by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    GroupLabel("Account number")
    RowGroup {
        GroupRow(
            title = if (revealed) AccountIdUtils.formatGrouped(accountId) else "•••• •••• •••• •••• ••••",
            supporting = {
                SupportingText(if (copied) "Copied; the clipboard clears in 60 s" else "Your only login. Keep it safe.")
            },
            look = RowLook(monoTitle = true),
        ) {
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
    }
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete account?") },
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

/** The account badge: its title and dated subtitle, and when that time ends ([renews] if it ends in a renewal). */
internal data class AccountBadgeUi(
    val drawableRes: Int,
    val title: String,
    val subtitle: String,
    val endSec: Long? = null,
    val renews: Boolean = false,
)

internal fun resolveBadge(account: AccountInfo?): AccountBadgeUi {
    if (account == null) {
        return AccountBadgeUi(R.drawable.badge_account_expired, "Checking status…", "Loading account details")
    }
    if (!account.active) {
        return AccountBadgeUi(R.drawable.badge_account_expired, "Account Inactive", "Top up or subscribe to connect")
    }
    val sub = account.googleSubscription ?: account.appleSubscription ?: account.stripeSubscription
    val subEnd = sub?.expiresAt ?: sub?.currentPeriodEnd
    val canceled = sub?.cancelAtPeriodEnd == true || sub?.autoRenewStatus == false
    // Top-up credit (or another source) that runs past a canceled subscription keeps the account active after it:
    // `current_expiry` is the account's real end, so the subscription's end is not the news.
    val outlivesSubscription = subEnd != null && (account.currentExpiry ?: 0) > subEnd
    if (sub?.active == true && !(canceled && outlivesSubscription)) {
        val expiry = subEnd ?: account.currentExpiry
        val dateText = expiry?.let(::formatEpochSeconds)
        return if (canceled) {
            AccountBadgeUi(
                R.drawable.badge_subscription_paused,
                "Subscription Canceled",
                dateText?.let { "Ends on $it" } ?: "Active",
                expiry,
            )
        } else {
            AccountBadgeUi(
                R.drawable.badge_subscription_active,
                "Active Subscription",
                dateText?.let { "Renews on $it" } ?: "Active",
                expiry,
                renews = true,
            )
        }
    }
    if (account.freeTrial?.active == true) {
        val end = account.freeTrial.expiresAt
        return AccountBadgeUi(R.drawable.badge_paid_up, "Free Trial", "Ends on ${formatEpochSeconds(end)}", end)
    }
    val expiry = account.currentExpiry ?: account.topUp?.creditExpiresAt
    return AccountBadgeUi(
        R.drawable.badge_paid_up,
        "Account Active",
        expiry?.let { "Active until ${formatEpochSeconds(it)}" } ?: "Active",
        expiry,
    )
}

private const val SECONDS_PER_DAY = 86_400L

/** Whole days from [nowSec] to [endSec] (a partial day does not count); 0 once past. */
internal fun daysLeft(endSec: Long, nowSec: Long): Long = maxOf(0L, (endSec - nowSec) / SECONDS_PER_DAY)

/** One row per source of time on the account (subscription, top-up credit, free trial) with the date it ends. */
internal fun timeRows(account: AccountInfo, nowSec: Long): List<Pair<String, String>> {
    val rows = ArrayList<Pair<String, String>>()
    val sub = account.googleSubscription ?: account.appleSubscription ?: account.stripeSubscription
    val subEnd = sub?.expiresAt ?: sub?.currentPeriodEnd
    if (sub?.active == true && subEnd != null) {
        val canceled = sub.cancelAtPeriodEnd == true || sub.autoRenewStatus == false
        rows += (if (canceled) "Subscription ends" else "Subscription renews") to formatEpochSeconds(subEnd)
    }
    account.topUp
        ?.takeIf { it.active && it.creditExpiresAt > nowSec }
        ?.let { rows += "Top-up credit until" to formatEpochSeconds(it.creditExpiresAt) }
    account.freeTrial?.takeIf { it.active }?.let { rows += "Free trial ends" to formatEpochSeconds(it.expiresAt) }
    return rows
}

private fun formatEpochSeconds(secs: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(secs))
