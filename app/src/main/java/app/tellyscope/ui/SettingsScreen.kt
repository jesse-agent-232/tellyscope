// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** App-wide choices, not tied to one TV. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    /** The user's own debloat-list source; null for tellyscope-devices. */
    listSource: String?,
    onListSource: (String?) -> Unit,
    /** Null hides the switch: this build doesn't check (screenshots). */
    checkUpdates: Boolean?,
    /** Alpha builds check every 5 minutes, release builds every 30 minutes. */
    alphaBuild: Boolean,
    onCheckUpdates: (Boolean) -> Unit,
    back: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    if (editing) SourceDialog(listSource, onSave = { editing = false; onListSource(it) }, onDismiss = { editing = false })
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the TV's page") }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Debloat lists", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Copy.listSource(listSource), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { editing = true }) { Text("Change") }
                        }
                    }
                }
            }
            if (checkUpdates != null) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(
                        Modifier.fillMaxWidth()
                            .toggleable(checkUpdates, role = Role.Switch, onValueChange = onCheckUpdates)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Check for updates", style = MaterialTheme.typography.titleSmall)
                            Text(
                                (if (alphaBuild) "Every 5 minutes on alpha builds" else "Every 30 minutes") +
                                    ", ask GitHub whether a newer Tellyscope is out. Nothing is downloaded.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(checked = checkUpdates, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

/** Edits the list source: an HTTPS folder holding `index.json`, or back to the default. */
@Composable
private fun SourceDialog(source: String?, onSave: (String?) -> Unit, onDismiss: () -> Unit) {
    var url by rememberSaveable { mutableStateOf(source.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where the lists come from") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "To use your own lists, fork tellyscope-devices, edit it, and paste the address of the folder that holds index.json, " +
                        "like https://raw.githubusercontent.com/you/tellyscope-devices/main/. Protected apps stay protected whatever a list says.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(url, { url = it }, placeholder = { Text("https://…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (source != null) TextButton(onClick = { onSave(null) }) { Text("Reset to default") }
            }
        },
        confirmButton = { Button(onClick = { onSave(url) }, enabled = url.isNotBlank()) { Text("Use these lists") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
