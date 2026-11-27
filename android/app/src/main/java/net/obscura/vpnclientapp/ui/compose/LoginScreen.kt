package net.obscura.vpnclientapp.ui.compose

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import net.obscura.vpnclientapp.R
import net.obscura.vpnclientapp.client.AccountIdUtils
import net.obscura.vpnclientapp.client.CoreStatus
import net.obscura.vpnclientapp.client.ErrorCodeException
import net.obscura.vpnclientapp.ui.CoreRepository
import net.obscura.vpnclientapp.ui.SecureWindowEffect
import net.obscura.vpnclientapp.ui.SensitiveClipboard
import net.obscura.vpnclientapp.ui.launchShareDebugBundle

@Composable
fun LoginScreen(
    repo: CoreRepository,
    status: CoreStatus?,
    trialAvailable: Boolean,
    playBilling: Boolean,
) {
    val accountId = status?.accountId
    val inNewAccountFlow = status?.inNewAccountFlow == true
    if (accountId != null && inNewAccountFlow) {
        AccountGenerationView(
            repo = repo,
            accountId = accountId,
            accountActive = status.account?.accountInfo?.active == true,
            playBilling = playBilling,
        )
    } else {
        LoginFormView(repo = repo, trialAvailable = trialAvailable)
    }
}

@Composable
private fun LoginFormView(repo: CoreRepository, trialAvailable: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var accountInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val runAction: (suspend () -> Unit) -> Unit = { block ->
        busy = true
        errorMsg = null
        scope.launch {
            runCatching { block() }.onFailure { errorMsg = loginErrorMessage(it) }
            busy = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.deco_signup_mobile),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
        )
        val cb =
            LoginBodyCallbacks(
                onInputChange = { raw ->
                    accountInput = AccountIdUtils.normalizeDigits(raw).take(AccountIdUtils.TOTAL_LENGTH)
                },
                onCreateAccount = { runAction { repo.createAccount() } },
                onLogin = { digits -> runAction { repo.login(digits, validate = true) } },
                onInvalid = { errorMsg = "Mistyped Account Number (must be 20 valid digits)" },
                onDebugBundle = {
                    scope.launch {
                        runCatching { repo.createDebugBundle(null) }
                            .onSuccess { launchShareDebugBundle(context, it, asEmail = false) }
                            .onFailure { errorMsg = "Debug bundle failed: ${it.message}" }
                    }
                },
            )
        LoginFormScrollContent(trialAvailable, accountInput, busy, errorMsg, cb)
    }
}

private data class LoginBodyCallbacks(
    val onInputChange: (String) -> Unit,
    val onCreateAccount: () -> Unit,
    val onLogin: (String) -> Unit,
    val onInvalid: () -> Unit,
    val onDebugBundle: () -> Unit,
)

@Composable
private fun LoginFormScrollContent(
    trialAvailable: Boolean,
    accountInput: String,
    busy: Boolean,
    errorMsg: String?,
    cb: LoginBodyCallbacks,
) {
    Column(
        modifier =
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LoginBrandBlock()
        Spacer(Modifier.height(12.dp))
        ObscuraGradientButton(
            text = if (trialAvailable) "Start Trial" else "Create an Account",
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = cb.onCreateAccount,
        )
        Spacer(Modifier.height(20.dp))
        ExistingAccountLoginBlock(accountInput, busy, cb.onInputChange, cb.onLogin, cb.onInvalid)
        errorMsg?.let {
            Spacer(Modifier.height(12.dp))
            NoticeBanner(text = it, kind = NoticeKind.ERROR)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = cb.onDebugBundle) { Text("Create Debug Bundle", color = LocalExtraColors.current.muted) }
    }
}

@Composable
private fun LoginBrandBlock() {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(48.dp)
        Text("Obscura VPN", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(16.dp))
    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://obscura.com/legal".toUri())) }) {
        Text(
            "By creating an account, you agree to our Terms of Service and Privacy Policy.",
            style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
            textAlign = TextAlign.Center,
            color = LocalExtraColors.current.muted,
        )
    }
}

@Composable
private fun ExistingAccountLoginBlock(
    accountInput: String,
    busy: Boolean,
    onInputChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onInvalid: () -> Unit,
) {
    OutlinedTextField(
        value = AccountIdUtils.formatGrouped(accountInput),
        onValueChange = onInputChange,
        label = { Text("Account Number") },
        placeholder = { Text("0000 - 0000 - 0000 - 0000 - 0000") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        onClick = {
            val digits = AccountIdUtils.normalizeDigits(accountInput)
            if (digits.length != AccountIdUtils.TOTAL_LENGTH || !AccountIdUtils.validChecksum(digits)) {
                onInvalid()
            } else {
                onSubmit(digits)
            }
        },
    ) {
        if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Log In")
    }
}

@Composable
private fun AccountGenerationView(
    repo: CoreRepository,
    accountId: String,
    accountActive: Boolean,
    playBilling: Boolean,
) {
    SecureWindowEffect(true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = remember(context, scope) { SensitiveClipboard(context, scope) }
    var copied by remember { mutableStateOf(false) }
    var paymentPressed by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val actions =
        GenBodyActions(
            onCopy = {
                clipboard.copyAccountNumber(accountId)
                copied = true
            },
            onPrimaryClick = {
                if (accountActive || paymentPressed) scope.launch { repo.finishNewAccountFlow() }
                else showConfirm = true
            },
            onHaveAccount = {
                scope.launch {
                    runCatching { repo.logout() }
                    runCatching { repo.finishNewAccountFlow() }
                }
            },
        )
    AccountGenerationColumn(accountId, copied, accountActive || paymentPressed, errorMsg, actions)

    if (showConfirm) {
        ConfirmPaymentDialog(
            onDismiss = { showConfirm = false },
            onProceed = {
                showConfirm = false
                paymentPressed = true
                if (playBilling) {
                    scope.launch {
                        runCatching { repo.purchasePlaySubscription(null) }
                            .onFailure { errorMsg = loginErrorMessage(it) }
                    }
                } else {
                    val uri = "https://obscura.com/pay#account_id=$accountId".toUri()
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            },
        )
    }
}

private data class GenBodyActions(
    val onCopy: () -> Unit,
    val onPrimaryClick: () -> Unit,
    val onHaveAccount: () -> Unit,
)

@Composable
private fun AccountGenerationColumn(
    accountId: String,
    copied: Boolean,
    doneReady: Boolean,
    errorMsg: String?,
    actions: GenBodyActions,
) {
    Column(
        modifier =
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GeneratedAccountBox(accountId, copied, actions.onCopy)
        Spacer(Modifier.height(20.dp))
        ObscuraGradientButton(
            text = if (doneReady) "Done" else "Continue to payment",
            modifier = Modifier.fillMaxWidth(),
            onClick = actions.onPrimaryClick,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = actions.onHaveAccount) { Text("I have an Account ID already") }
        errorMsg?.let {
            Spacer(Modifier.height(12.dp))
            NoticeBanner(text = it, kind = NoticeKind.ERROR)
        }
    }
}

@Composable
private fun GeneratedAccountBox(accountId: String, copied: Boolean, onCopy: () -> Unit) {
    AppIcon(56.dp)
    Spacer(Modifier.height(16.dp))
    Text("Your Account Number", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
    ObscuraCard {
        Text(
            AccountIdUtils.formatGrouped(accountId),
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontMono),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(12.dp))
    Button(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
        Icon(AppIcons.Copy, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (copied) "Copied Account Number" else "Copy Account Number")
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Please write down your account number or store it in an encrypted vault. It is the only way to log in.",
        style = MaterialTheme.typography.bodySmall,
        color = LocalExtraColors.current.muted,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ConfirmPaymentDialog(onDismiss: () -> Unit, onProceed: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmation") },
        text = { Text("Please confirm that you have stored your account number somewhere secure.") },
        confirmButton = { Button(onClick = onProceed) { Text("Continue to payment") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun loginErrorMessage(t: Throwable): String {
    val code = (t as? ErrorCodeException)?.errorCode ?: return t.message ?: "Login failed"
    return when (code) {
        "apiInvalidAccountId" -> "Account Number is invalid."
        "apiRateLimitExceeded" -> "API rate-limit exceeded. Please try again later."
        "apiSignupLimitExceeded" -> "Sign up limit exceeded. Please try again in a few hours."
        "apiUnreachable" -> "Unable to reach Obscura API. Check your internet connection."
        "apiNoLongerSupported" -> "This client version is too old."
        else -> "Error: $code"
    }
}
