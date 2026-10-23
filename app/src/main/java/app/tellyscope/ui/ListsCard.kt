// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.tellyscope.data.Suggested
import app.tellyscope.data.Suggestions

/** Body of the "Suggested for this TV" section: one line on what the list offers, and the way in. */
@Composable
fun SuggestionsSummary(lists: Suggestions?, disabling: Boolean, onDebloat: () -> Unit) {
    when (lists) {
        null -> Muted("Looking up this TV's list…")
        Suggestions.Unavailable -> Muted(
            "Couldn't load the device lists. They download the first time you open a TV; check the phone's internet and open this TV again.",
        )
        is Suggestions.Pick -> {
            Text("Tellyscope doesn't know this model yet. Pick your TV to see what's usually safe to turn off.", style = MaterialTheme.typography.bodyMedium)
            DebloatButton(disabling, onDebloat)
        }
        is Suggestions.Ready -> {
            val n = lists.groups.sumOf { it.packages.size }
            if (n == 0) {
                Muted("Nothing on the list for ${lists.list.name} is still on here.")
            } else {
                Text("$n ${if (n == 1) "app" else "apps"} usually safe to turn off on this TV.", style = MaterialTheme.typography.bodyMedium)
            }
            DebloatButton(disabling, onDebloat)
        }
    }
    if (lists is Suggestions.Pick || lists is Suggestions.Ready) Muted("Lists adapted from cleanyourtv (MIT).")
}

@Composable
private fun DebloatButton(disabling: Boolean, onDebloat: () -> Unit) {
    Button(onClick = onDebloat, enabled = !disabling, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(if (disabling) "Disabling…" else "Debloat")
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Debloat in two steps. First the TV's model: the matched list is chosen, any other can be, and
 * an unknown model starts with nothing chosen. Then every app that list would turn off, ticked as
 * the list says, with a final confirm that names each one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebloatScreen(
    lists: Suggestions?,
    onPick: (String) -> Unit,
    onDisable: (List<String>) -> Unit,
    back: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val ready = lists as? Suggestions.Ready
    val devices = when (lists) {
        is Suggestions.Pick -> lists.devices
        is Suggestions.Ready -> lists.devices
        else -> emptyList()
    }
    var step by rememberSaveable { mutableIntStateOf(1) }
    var chosen by rememberSaveable { mutableStateOf(ready?.list?.id) }
    var q by rememberSaveable { mutableStateOf("") }
    // Ticks start from the list and reset whenever it reloads.
    var ticked by remember(lists) {
        mutableStateOf(ready?.groups.orEmpty().flatMap { it.packages }.filter { it.ticked }.map { it.pkg }.toSet())
    }
    var confirming by remember { mutableStateOf(false) }
    val showing = ready?.takeIf { it.list.id == chosen }
    if (confirming && showing != null) {
        // The dialog's list is exactly what gets disabled.
        val apps = showing.groups.flatMap { it.packages }.filter { it.pkg in ticked }
        DisableDialog(apps, onDisable = { confirming = false; onDisable(apps.map { it.pkg }); back() }, onDismiss = { confirming = false })
    }
    val goBack = { if (step == 2) step = 1 else back() }
    BackHandler(onBack = goBack)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (step == 1) "Which TV is this?" else "Review the apps")
                        Text("Debloat · step $step of 2", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    if (step == 1) {
                        Button(
                            onClick = { chosen?.let { if (it != ready?.list?.id) onPick(it) }; step = 2 },
                            enabled = chosen != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Next") }
                    } else {
                        Button(
                            onClick = { confirming = true },
                            enabled = showing != null && ticked.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Disable ${ticked.size} ${if (ticked.size == 1) "app" else "apps"}") }
                    }
                }
            }
        },
    ) { pad ->
        if (step == 1) {
            // The matched list first, then the rest by name.
            val shown = devices
                .filter { q.isBlank() || it.name.contains(q.trim(), ignoreCase = true) }
                .sortedWith(compareBy({ it.id != ready?.list?.id }, { it.name.lowercase() }))
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                item {
                    Text(
                        if (ready != null) "Tellyscope matched this TV to the ${ready.list.name} list. Pick another model if that's wrong."
                        else "Tellyscope doesn't know this model yet. Pick the closest match.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        q, { q = it },
                        placeholder = { Text("Brand or model") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    // Empty while a picked list reloads, if they came back to change it.
                    if (devices.isEmpty()) Muted("Loading the lists…")
                    else if (shown.isEmpty()) Muted("No list matches \"${q.trim()}\".")
                }
                items(shown, key = { it.id }) { d ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { chosen = d.id }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(d.id == chosen, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(d.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showing == null) {
                    item {
                        Muted(
                            if (lists == Suggestions.Unavailable) "Couldn't load that list. Check the phone's internet and try again."
                            else "Loading the list…",
                        )
                    }
                    return@LazyColumn
                }
                item {
                    Text(
                        "From the list for ${showing.list.name}. Untick anything you use. Nothing is deleted: each app is hidden, and History can turn it back on.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!showing.sdkMatches) Muted(
                        showing.list.testedOs?.let { "Tested on $it, not this TV's Android version, so nothing is ticked. Tick only what you know you don't use." }
                            ?: "This list doesn't say which Android it was tested on, so nothing is ticked. Tick only what you know you don't use.",
                    )
                    if (showing.groups.isEmpty()) Muted("Nothing on the list is still on here.")
                }
                showing.groups.forEach { g ->
                    item(key = "g:" + g.title) { Text(g.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                    items(g.packages, key = { it.pkg }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                                .clickable { ticked = if (p.pkg in ticked) ticked - p.pkg else ticked + p.pkg },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(p.pkg in ticked, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    listOfNotNull(p.pkg, p.note, "optional".takeIf { p.optional }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DisableDialog(apps: List<Suggested>, onDisable: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disable ${apps.size} ${if (apps.size == 1) "app" else "apps"}?") },
        text = {
            // Every name, so a list can't slip something in behind a count.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Each one is hidden and stops running. Nothing is deleted; every one shows in History, where you can turn it back on.")
                LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(apps, key = { it.pkg }) { a ->
                        Column {
                            Text(a.label, style = MaterialTheme.typography.bodyLarge)
                            Text(a.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDisable) { Text("Yes, disable them") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** About and licences; [commit] is the cleanyourtv commit the downloaded lists were built from. */
@Composable
fun AboutDialog(commit: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About Tellyscope") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Free software under the GNU GPL v3.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Device lists are adapted from cleanyourtv by Gathin (MIT licence), " +
                        (commit?.let { "commit ${it.take(12)}." } ?: "once they have downloaded."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Uses Kadb (Apache 2.0) and spake2-java (GPL v3). Full licence texts are in the NOTICE file.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
