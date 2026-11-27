package net.obscura.vpnclientapp.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * What went wrong, as Proton's "Report an issue" asks before anything else: each kind brings fixes to try first and a
 * question that gets support the detail it needs.
 */
enum class ProblemKind(val label: String, val question: String, val tips: List<String>) {
    CANT_CONNECT(
        "Can't connect",
        "Which network were you on (Wi-Fi, mobile, at work)? What does the VPN tab say?",
        listOf(
            "Turn on Settings › Protocol › Switch automatically, or choose TCP/TLS.",
            "Choose Fastest entry relay on the Locations tab.",
        ),
    ),
    DROPS(
        "Keeps disconnecting",
        "When does it drop: when the screen is off, when you change network, or at random?",
        listOf(
            "Set Settings › Android › Battery use to Unrestricted.",
            "Turn on Settings › Android › Always-on VPN.",
        ),
    ),
    SLOW(
        "Slow",
        "Which location, and how slow (a speed test result helps)?",
        listOf("Choose an exit closer to you.", "If your network allows UDP, try the Speed protocol."),
    ),
    SITE(
        "A site or app fails",
        "Which site or app, and what happens?",
        listOf(
            "Some sites block VPN addresses: try another exit.",
            "Exclude the app under Settings › Split tunnelling to test it without the VPN.",
        ),
    ),
    ACCOUNT(
        "Account or payment",
        "What did you expect to see, and what do you see?",
        listOf("Account › Refresh account status."),
    ),
    OTHER("Something else", "What happened, and what did you expect?", emptyList()),
}

/** The text sent with a bundle: the kind of problem first, so support can sort it, then the user's words. */
fun reportText(kind: ProblemKind?, details: String): String? {
    val words = details.trim()
    return when {
        kind == null -> words.ifBlank { null }
        words.isBlank() -> "[${kind.label}]"
        else -> "[${kind.label}] $words"
    }
}

/** The kinds as chips, then the chosen kind's fixes to try first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProblemPicker(selected: ProblemKind?, onSelect: (ProblemKind?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        ProblemKind.entries.forEach { kind ->
            FilterChip(
                selected = kind == selected,
                onClick = { onSelect(if (kind == selected) null else kind) },
                label = { Text(kind.label) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
            )
        }
    }
    val tips = selected?.tips.orEmpty()
    if (tips.isNotEmpty()) {
        Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Try first", style = MaterialTheme.typography.labelLarge, color = ObscuraPalette.Orange)
            tips.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
