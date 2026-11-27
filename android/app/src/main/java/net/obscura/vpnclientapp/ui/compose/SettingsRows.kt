package net.obscura.vpnclientapp.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.obscura.vpnclientapp.ui.InstalledApps

/** A setting that is on or off: the whole row toggles, the switch on the right says which. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    GroupRow(
        title = title,
        supporting = subtitle?.let { { SupportingText(it) } },
        onClick = if (enabled) ({ onChange(!checked) }) else null,
    ) {
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** One of several choices: the chosen one carries the orange check, as in the location picker. */
@Composable
fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    GroupRow(title = title, supporting = subtitle?.let { { SupportingText(it) } }, onClick = onClick) {
        if (selected) SelectedMark()
    }
}

/** A row that opens something (a dialog, a sheet, Android's settings); [warn] colours its status line. */
@Composable
fun NavRow(title: String, subtitle: String?, warn: Boolean = false, onClick: () -> Unit) {
    GroupRow(
        title = title,
        supporting =
            subtitle?.let {
                {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (warn) ObscuraPalette.Orange else LocalExtraColors.current.muted,
                    )
                }
            },
        onClick = onClick,
    ) {
        Icon(
            AppIcons.ChevronRight,
            contentDescription = null,
            tint = LocalExtraColors.current.muted,
            modifier = Modifier.padding(end = 8.dp).size(18.dp),
        )
    }
}

/** An action, such as "Rotate key now": orange (or [color]), no chevron. */
@Composable
fun ActionRow(title: String, color: Color = ObscuraPalette.Orange, onClick: () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 20.dp),
    )
}

/** Free content inside a group (a segmented control, chips, an editor), padded like the rows. */
@Composable
fun GroupBlock(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) { content() }
}

/** The small print under a group. */
@Composable
fun GroupFooter(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalExtraColors.current.muted,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp),
    )
}

/** What an [AddressListEditor] edits: its title, the hint, and how a typed entry is checked and normalized. */
data class AddressEditorSpec(
    val title: String,
    val placeholder: String,
    val invalidMessage: String,
    val normalize: (String) -> String?,
)

/** A list of addresses: a pill field with Add, then each entry with a remove button. */
@Composable
fun AddressListEditor(
    spec: AddressEditorSpec,
    items: List<String>,
    enabled: Boolean,
    onItemsChange: (List<String>) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    val add = {
        val normalized = spec.normalize(input.trim())
        if (normalized == null) {
            inputError = spec.invalidMessage
        } else {
            if (normalized !in items) onItemsChange(items + normalized)
            input = ""
        }
    }
    GroupBlock {
        Text(spec.title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    inputError = null
                },
                placeholder = { Text(spec.placeholder, style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                enabled = enabled,
                isError = inputError != null,
                supportingText = inputError?.let { { Text(it) } },
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = LocalExtraColors.current.border),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = add, enabled = enabled && input.isNotBlank()) {
                Text("Add", color = if (enabled && input.isNotBlank()) ObscuraPalette.Orange else Color.Unspecified)
            }
        }
        items.forEach { entry -> AddressEntry(entry, enabled) { onItemsChange(items - entry) } }
    }
}

@Composable
private fun AddressEntry(entry: String, enabled: Boolean, onRemove: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            entry,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontMono),
            modifier = Modifier.weight(1f),
        )
        IconButton(enabled = enabled, onClick = onRemove) {
            Icon(AppIcons.Close, contentDescription = "Remove $entry", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun HostInputDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text("Leave blank for the default") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onSave(value.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitAppsSheet(selectedApps: Set<String>, onUpdateApps: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApps.Item>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { apps = InstalledApps.query(context, context.packageName) }
    val filtered =
        remember(apps, search, showSystem) {
            val q = search.trim().lowercase()
            apps.filter { app ->
                (showSystem || !app.isSystem) &&
                    (q.isEmpty() || app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q))
            }
        }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Choose apps", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search installed apps") },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow("Show system apps", null, showSystem) { showSystem = it }
            LazyColumn(modifier = Modifier.height(380.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(app, app.packageName in selectedApps) { checked ->
                        onUpdateApps(if (checked) selectedApps + app.packageName else selectedApps - app.packageName)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApps.Item, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(app.label, style = MaterialTheme.typography.bodyMedium)
            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = LocalExtraColors.current.muted)
        }
    }
}
