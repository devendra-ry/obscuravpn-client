package net.obscura.vpnclientapp.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun ObscuraGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDanger: Boolean = false,
) {
    val shape = RoundedCornerShape(24.dp)
    if (isDanger) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White,
                ),
            modifier = modifier.heightIn(min = 48.dp),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        val bg =
            if (enabled) ObscuraPalette.Gradient
            else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant)
        Box(
            modifier =
                modifier
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(bg)
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ObscuraCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val extra = LocalExtraColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, extra.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

enum class NoticeKind {
    INFO,
    WARNING,
    ERROR,
}

@Composable
fun NoticeBanner(
    text: String,
    kind: NoticeKind,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val bg =
        when (kind) {
            NoticeKind.INFO -> MaterialTheme.colorScheme.surfaceVariant
            NoticeKind.WARNING -> MaterialTheme.colorScheme.primaryContainer
            NoticeKind.ERROR -> MaterialTheme.colorScheme.errorContainer
        }
    val fg =
        when (kind) {
            NoticeKind.INFO -> MaterialTheme.colorScheme.onSurface
            NoticeKind.WARNING -> MaterialTheme.colorScheme.onPrimaryContainer
            NoticeKind.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = AppIcons.Warning,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 2.dp).size(18.dp),
                    tint = fg,
                )
                Text(text = text, style = MaterialTheme.typography.bodySmall, color = fg)
            }
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(actionLabel, color = ObscuraPalette.Orange)
                }
            }
        }
    }
}

@Composable
fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = LocalExtraColors.current.border,
    )
}

@Composable
fun KeyValueRow(label: String, value: String, mono: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalExtraColors.current.muted,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (mono) FontMono else MaterialTheme.typography.bodyMedium.fontFamily,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun LeadingIconLabel(icon: ImageVector, label: String, tint: Color = LocalExtraColors.current.muted) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
