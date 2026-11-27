package net.obscura.vpnclientapp.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.obscura.vpnclientapp.client.AccountIdUtils

/*
 * Grouped lists as in Ombra: rows in one rounded card with hairlines between them, 64dp rows, a small framed flag,
 * the state in the supporting line (" · Connected" in green) and an orange check for the chosen row.
 */

/** Rows in one rounded card. Put [GroupDivider] between them. */
@Composable
fun RowGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LocalExtraColors.current.border),
    ) {
        Column(content = content)
    }
}

/** The hairline between two rows of a [RowGroup]; [indent] lines it up with the text of indented rows. */
@Composable
fun GroupDivider(indent: Dp = 0.dp) {
    HorizontalDivider(modifier = Modifier.padding(start = indent), color = LocalExtraColors.current.border)
}

/** The caption above a group: small, orange and semibold, as in Ombra. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = ObscuraPalette.Orange,
        modifier = modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp),
    )
}

/** A country flag at one size, rounded, with a hairline so white flags stay visible. */
@Composable
fun FlagBadge(countryCode: String) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier =
            Modifier.width(30.dp)
                .heightIn(min = 21.dp, max = 21.dp)
                .clip(shape)
                .border(1.dp, LocalExtraColors.current.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(AccountIdUtils.countryFlagEmoji(countryCode), fontSize = 26.sp, maxLines = 1)
    }
}

/** The supporting line of a row, with " · Connected" in green for the place in use. */
@Composable
fun SupportingText(text: String, connected: Boolean = false) {
    val success = LocalExtraColors.current.success
    Text(
        buildAnnotatedString {
            append(text)
            if (connected) {
                withStyle(SpanStyle(color = success)) { append(if (text.isEmpty()) "Connected" else " · Connected") }
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = LocalExtraColors.current.muted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The orange check of the chosen row. */
@Composable
fun SelectedMark() {
    Icon(AppIcons.Check, contentDescription = "Selected", tint = ObscuraPalette.Orange, modifier = Modifier.size(20.dp))
}

/** The chevron that folds a row's children in and out; it turns rather than swapping icons. */
@Composable
fun FoldButton(open: Boolean, label: String, onToggle: () -> Unit) {
    val angle by animateFloatAsState(if (open) 180f else 0f, label = "fold")
    IconButton(onClick = onToggle) {
        Icon(
            AppIcons.ChevronDown,
            contentDescription = label,
            tint = LocalExtraColors.current.muted,
            modifier = Modifier.size(18.dp).rotate(angle),
        )
    }
}

/** How a [GroupRow] looks: what leads it, how far it is indented under its parent, a monospace title for ids. */
data class RowLook(
    val leading: (@Composable () -> Unit)? = null,
    val startPadding: Dp = 18.dp,
    val monoTitle: Boolean = false,
)

/** One row of a [RowGroup]: a title and supporting line, then trailing content (a check, a switch, buttons). */
@Composable
fun GroupRow(
    title: String,
    supporting: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    look: RowLook = RowLook(),
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = look.startPadding, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        look.leading?.let { leading ->
            Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) { leading() }
        }
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                title,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = if (look.monoTitle) FontMono else MaterialTheme.typography.bodyLarge.fontFamily,
                    ),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            supporting?.invoke()
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}
