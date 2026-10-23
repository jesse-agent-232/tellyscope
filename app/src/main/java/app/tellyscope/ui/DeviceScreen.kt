// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tellyscope.adb.TvFile
import app.tellyscope.adb.TvFolder
import app.tellyscope.data.Action
import app.tellyscope.data.AppInfo
import app.tellyscope.data.AppNames
import app.tellyscope.data.Device
import app.tellyscope.data.DeviceState
import app.tellyscope.data.HistoryBook
import app.tellyscope.data.HistoryEntry
import app.tellyscope.data.Proc
import app.tellyscope.data.Snapshot
import app.tellyscope.data.Suggestions
import app.tellyscope.data.deviceKind
import app.tellyscope.ui.theme.SmallNumber

data class DeviceActions(
    val select: (String) -> Unit = {},
    val addDevice: () -> Unit = {},
    val settings: () -> Unit = {},
    val showOffline: (Boolean) -> Unit = {},
    val query: (String) -> Unit = {},
    val propose: (String, Action) -> Unit = { _, _ -> },
    val confirm: () -> Unit = {},
    val cancel: () -> Unit = {},
    val undo: (HistoryEntry) -> Unit = {},
    val reconnect: () -> Unit = {},
    val forget: (String) -> Unit = {},
    val rename: (String, String?) -> Unit = { _, _ -> },
    val restoreAll: () -> Unit = {},
    /** Opens the system picker for an APK, split APKs or a bundle. */
    val pickApp: () -> Unit = {},
    val allowDowngrade: (Boolean) -> Unit = {},
    val install: () -> Unit = {},
    val closeSideload: () -> Unit = {},
    val openInstalled: () -> Unit = {},
    val folder: (TvFolder) -> Unit = {},
    /** Shows that folder, then opens the system picker for a file to send into it. */
    val pickFile: (TvFolder) -> Unit = {},
    /** Asks where to save [TvFile] on the phone, then copies it. */
    val saveFile: (TvFile) -> Unit = {},
    val cancelTransfer: () -> Unit = {},
    val remote: () -> Unit = {},
    /** Opens the Debloat flow: pick the TV's list, then review the apps it turns off. */
    val debloat: () -> Unit = {},
    /** Opens the Advanced screen: every number, per-app CPU and RAM, processes and network. */
    val advanced: () -> Unit = {},
    /** Ad-blocking Private DNS through the given hostname; null goes back to Automatic. */
    val setDns: (String?) -> Unit = {},
    val setFastAnimations: (Boolean) -> Unit = {},
    val clearCaches: () -> Unit = {},
    /** Asks where to save a screenshot of the TV on the phone, then takes it. */
    val screenshot: () -> Unit = {},
    /** Allow or Deny on another device's debugging prompt, and whether to always allow it. */
    val answerRequest: (allow: Boolean, always: Boolean) -> Unit = { _, _ -> },
    /** Hides that prompt on the phone without answering it; the TV keeps asking. */
    val dismissRequest: () -> Unit = {},
)

/** Off while the remote is made faster: the device page hides its Remote button. */
internal const val REMOTE_ENABLED = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    state: UiState,
    actions: DeviceActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val device = state.selected ?: return
    val ui = state.device
    var searching by rememberSaveable { mutableStateOf(false) }
    val q = state.query.trim().lowercase()
    val offline = device.state != DeviceState.Online

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (searching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = state.query,
                            onValueChange = actions.query,
                            placeholder = { Text("Search apps") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    actions = {
                        IconButton(onClick = { searching = false; actions.query("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Your TV", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    actions = {
                        if (REMOTE_ENABLED) TextButton(onClick = actions.remote, enabled = !offline) { Text("Remote") }
                        TextButton(onClick = actions.advanced) { Text("Advanced") }
                        IconButton(onClick = { searching = true }, enabled = !offline) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                )
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val snap = ui.snapshot
            // A reading from before the TV went away is stale: it doesn't speak for the TV now.
            val live = snap?.takeIf { !offline }
            item { TvCard(state, live, actions) }
            val restart = state.restarting[device.id]
            if (restart != null) {
                item { RestartingCard(restart, state.now) }
            } else if (device.state == DeviceState.NotAllowed) {
                item { NotAllowedCard(actions.reconnect) }
            } else if (offline) {
                item { OfflineCard(device, state.now, actions.reconnect) }
            }
            if (live != null) {
                item { SystemWidget(live) }
            } else if (offline && snap == null) {
                item {
                    Text(
                        Copy.noStats(device.state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!offline && q.isEmpty()) {
                item {
                    ActionTiles(
                        actions,
                        canDebloat = ui.lists is Suggestions.Pick || ui.lists is Suggestions.Ready,
                        disabling = ui.busy == DISABLING,
                    )
                }
                val count = (ui.lists as? Suggestions.Ready)?.groups?.sumOf { it.packages.size } ?: 0
                section("lists", "Suggested for this TV", count, expandedByDefault = true) {
                    SuggestionsSummary(ui.lists)
                }
            }
            // An offline TV can't list anything: sections with nothing in them stay out of the way.
            // Protected apps can't be changed, so they wait in their own folded section below.
            val (locked, changeable) = ui.apps
                .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.contains(q) }
                .sortedWith(compareBy({ !it.enabled }, { it.system }, { it.label }))
                .partition { it.protectedReason != null }
            if (!offline || ui.apps.isNotEmpty()) section(
                key = "apps",
                title = "Apps",
                count = ui.apps.count { it.protectedReason == null },
                expandedByDefault = true,
                forceOpen = q.isNotEmpty(),
            ) {
                if (changeable.isEmpty()) EmptyLine(q)
                changeable.forEach { app ->
                    AppRow(app, snap?.procs.orEmpty(), ui.pending, ui.busy == app.packageName, actions, homeLabel(ui.apps, app))
                }
            }
            if (ui.apps.any { it.protectedReason != null } && (q.isEmpty() || locked.isNotEmpty())) section(
                key = "protected",
                title = Copy.CANT_CHANGE,
                count = ui.apps.count { it.protectedReason != null },
                forceOpen = q.isNotEmpty(),
            ) {
                locked.forEach { app ->
                    AppRow(app, snap?.procs.orEmpty(), ui.pending, ui.busy == app.packageName, actions, homeLabel(ui.apps, app))
                }
            }
            if (!offline && q.isEmpty()) section("files", "Files on the TV", ui.files.list?.size ?: 0) {
                FilesBlock(ui.files, state.now, actions)
            }
            if (!offline && q.isEmpty()) section("tweaks", Copy.TWEAKS, 0) {
                TweaksBlock(ui, actions)
            }
            section("history", "History", ui.history.size) {
                HistoryList(ui.history, state.now, actions.undo, restoring = ui.busy == RESTORING, onRestoreAll = actions.restoreAll)
            }
        }
        if (!offline) ui.request?.let { RequestDialog(it, ui.busy == ANSWERING, actions.answerRequest, actions.dismissRequest) }
        ui.sideload?.let { SideloadDialog(it, actions) }
        ui.transfer?.let { TransferDialog(it, actions.cancelTransfer) }
    }
}

/**
 * The TV at a glance, with room to breathe: which TV (tap to switch), how it's doing, and the
 * few facts people look up. Everything else is on Advanced.
 */
@Composable
private fun TvCard(state: UiState, live: Snapshot?, actions: DeviceActions) {
    val device = state.selected ?: return
    val facts = listOfNotNull(
        "IP address" to device.hit.ip,
        live?.uptimeSeconds?.takeIf { it > 0 }?.let { "Up for" to Copy.upFor(it) },
        device.android?.let { "Android" to it },
        // Not live: the software doesn't change while the TV is away.
        state.device.snapshot?.softwareMonth?.let { "Last software update" to Copy.month(it) },
    )
    Card(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DevicePicker(state, actions)
            if (live != null) {
                HealthLine(Copy.health(live, heaviest(live, state.device.apps)))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StateDot(device.state)
                    Spacer(Modifier.width(8.dp))
                    Text(Copy.state(device.state), style = MaterialTheme.typography.titleMedium)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                facts.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (k, v) -> Fact(k, v, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The TV's vitals in one small card: internal storage as a full bar, since it decides whether an
 * install fits, then CPU, RAM, temperature and network speed as plain numbers.
 */
@Composable
private fun SystemWidget(s: Snapshot) {
    val dash = "\u2014"
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            val used = s.storageUsedGb
            val total = s.storageTotalGb?.takeIf { it > 0 }
            if (used != null && total != null) {
                Row {
                    Text("Internal storage", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${Copy.gbOf(used, total)} used", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Copy.free(s)?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
                val full = used / total
                LinearProgressIndicator(
                    progress = { full.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp).height(8.dp),
                    color = if (full >= Copy.FULL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Row {
                Vital("CPU", "${s.cpuPercent}%", Modifier.weight(1f))
                Vital("RAM", if (s.ramTotalMb > 0) "${s.ramUsedMb * 100 / s.ramTotalMb}%" else dash, Modifier.weight(1f))
                Vital("Temp", s.tempC?.let { "$it\u00b0C" } ?: dash, Modifier.weight(1f))
                Vital("Network", s.netBytesPerSec?.let(Copy::speed) ?: dash, Modifier.weight(1.3f))
            }
        }
    }
}

@Composable
private fun Vital(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

@Composable
private fun DevicePicker(state: UiState, actions: DeviceActions) {
    var open by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    val device = state.selected ?: return
    if (forgetting) ForgetDialog(name(device), onForget = { forgetting = false; actions.forget(device.id) }, onDismiss = { forgetting = false })
    if (renaming) RenameDialog(device.nickname.orEmpty(), networkName(device), onSave = { renaming = false; actions.rename(device.id, it) }, onDismiss = { renaming = false })
    if (about) AboutDialog(
        when (val l = state.device.lists) {
            is Suggestions.Pick -> l.commit
            is Suggestions.Ready -> l.commit
            else -> null
        },
        onDismiss = { about = false },
    )
    val offlineCount = state.devices.count { it.state != DeviceState.Online }
    Box {
        Row(
            Modifier.clip(MaterialTheme.shapes.large).clickable { open = true }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KindBadge(device)
            Column(Modifier.weight(1f, fill = false)) {
                Text(name(device), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val made = made(device.manufacturer ?: device.hit.maker, device.model)
                if (made.isNotEmpty()) {
                    Text(made, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch TV")
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            state.devices
                .filter { state.showOffline || it.state == DeviceState.Online || it.id == state.selectedId }
                .forEach { d ->
                    DropdownMenuItem(
                        // Name over state, like the top bar: a long name gets the full width.
                        text = {
                            Column {
                                Text(name(d), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StateDot(d.state)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (d.state == DeviceState.Online) Copy.state(d.state)
                                        else d.lastSeen?.let { "${Copy.state(d.state)}, seen ${Copy.ago(it, state.now)}" } ?: Copy.state(d.state),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        leadingIcon = { Icon(DeviceIcons.of(deviceKind(d)), contentDescription = deviceKind(d).word()) },
                        onClick = { open = false; actions.select(d.id) },
                    )
                }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Show offline ($offlineCount)") },
                leadingIcon = { Checkbox(state.showOffline, onCheckedChange = null) },
                onClick = { actions.showOffline(!state.showOffline) },
            )
            DropdownMenuItem(
                text = { Text("Add a TV") },
                leadingIcon = { Icon(Icons.Default.Add, null) },
                onClick = { open = false; actions.addDevice() },
            )
            DropdownMenuItem(
                text = { Text("Rename this TV") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = { open = false; renaming = true },
            )
            DropdownMenuItem(
                text = { Text("Forget this TV") },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = { open = false; forgetting = true },
            )
            DropdownMenuItem(
                text = { Text("Settings") },
                leadingIcon = { Icon(Icons.Default.Settings, null) },
                onClick = { open = false; actions.settings() },
            )
            DropdownMenuItem(
                text = { Text("About & licences") },
                leadingIcon = { Icon(Icons.Default.Info, null) },
                onClick = { open = false; about = true },
            )
        }
    }
}

/** Undoing a Turn on disables the app; it names the app and asks like any other Disable. */
@Composable
fun UndoDisableDialog(entry: HistoryEntry, onDisable: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disable ${entry.label}?") },
        text = { Text(Copy.explain(Action.Disable, entry.label, entry.packageName)) },
        confirmButton = { Button(onClick = onDisable) { Text(Copy.confirm(Action.Disable)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Restore only touches what Tellyscope turned off; the dialog says so before anything runs. */
@Composable
fun RestoreDialog(count: Int, onRestore: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Turn $count ${if (count == 1) "app" else "apps"} back on?") },
        text = {
            Text(
                "Turns back on every app Tellyscope disabled on this TV. " +
                    "Apps that were already off before Tellyscope's first change stay off.",
            )
        },
        confirmButton = { Button(onClick = onRestore) { Text("Turn back on") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "XGIMI · Horizon Pro": many models already start with the maker's name, so it isn't said twice. */
private fun made(maker: String?, model: String?): String {
    val m = if (maker != null && model != null && model.startsWith(maker, ignoreCase = true)) model.drop(maker.length).trim() else model
    return listOfNotNull(maker, m?.takeIf { it.isNotEmpty() }).joinToString(" · ")
}

private fun name(d: Device) = d.nickname ?: networkName(d)

private fun networkName(d: Device) = d.hit.hostname ?: d.model ?: d.hit.ip

/** Stored on this phone only, per TV; an empty name goes back to what the network calls it. */
@Composable
fun RenameDialog(current: String, networkName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename this TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Only this phone sees the name. Nothing on the TV changes.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(app.tellyscope.data.NameBook.MAX_LENGTH) },
                    singleLine = true,
                    placeholder = { Text(networkName) },
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What kind of device it is, on a tile: TV, projector or streaming stick. Generic shapes, never a maker's logo. */
@Composable
private fun KindBadge(d: Device) {
    val kind = deviceKind(d)
    Box(
        Modifier.padding(end = 14.dp).size(56.dp).clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(DeviceIcons.of(kind), contentDescription = kind.word(), Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Forgetting only touches this phone; the TV keeps its own list of allowed computers. */
@Composable
fun ForgetDialog(name: String, onForget: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forget $name?") },
        text = { Text("Tellyscope stops reconnecting to it and removes it from this phone. Nothing on the TV changes. You can add it again from a scan.") },
        confirmButton = { Button(onClick = onForget) { Text("Forget") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StateDot(state: DeviceState) {
    val c = when (state) {
        DeviceState.Online -> MaterialTheme.colorScheme.primary
        DeviceState.Waiting, DeviceState.Found, DeviceState.NeedsPairing -> MaterialTheme.colorScheme.tertiary
        DeviceState.Offline -> MaterialTheme.colorScheme.outline
        DeviceState.NotAllowed -> MaterialTheme.colorScheme.error
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(c))
}

/** The TV refused (revoked access, a reset, or Deny); retrying stopped so the prompt doesn't nag. */
@Composable
private fun NotAllowedCard(onConnect: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardTitle(Icons.Default.Warning, "The TV didn't allow this phone")
            Text(
                "Its debugging access was turned off, reset, or denied. Tellyscope stopped asking so the TV isn't flooded with prompts.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onConnect) { Text("Connect again") }
            Text(
                "Then press Allow on the TV, and tick \"Always allow from this computer\".",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CardTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun OfflineCard(device: Device, now: Long, onReconnect: () -> Unit) {
    // Material 3 has no warning role; tertiary is the standard stand-in, kept apart from error.
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                CardTitle(Icons.Default.Info, "Offline")
                Text(
                    "Last seen ${device.lastSeen?.let { Copy.ago(it, now) } ?: "a while ago"}. It reconnects on its own when the TV is back on.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = onReconnect) { Text("Reconnect") }
        }
    }
}

/** App names by memory use, largest first, for the health line. */
private fun heaviest(snap: Snapshot, apps: List<AppInfo>): List<String> {
    val labels = apps.associate { it.packageName to it.label }
    return snap.procs.filter { it.packageName != null }
        .groupBy { labels[it.packageName] ?: AppNames.fallback(it.packageName!!) }
        .mapValues { (_, ps) -> ps.sumOf { it.ramMb } }
        .entries.sortedByDescending { it.value }.map { it.key }
}

@Composable
private fun HealthLine(h: Copy.Health) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (h.ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            Modifier.size(24.dp),
            tint = if (h.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(8.dp))
        Text(h.text, style = MaterialTheme.typography.titleMedium)
    }
}

/** A collapsible card section. */
private fun LazyListScope.section(
    key: String,
    title: String,
    count: Int,
    expandedByDefault: Boolean = false,
    forceOpen: Boolean = false,
    body: @Composable () -> Unit,
) {
    item(key = key) {
        var open by rememberSaveable { mutableStateOf(expandedByDefault) }
        val shown = open || forceOpen
        val turn by animateFloatAsState(if (shown) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chevron")
        Card(
            Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (count > 0) Text("$count", style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = if (shown) "Collapse" else "Expand", Modifier.rotate(turn))
            }
            AnimatedVisibility(shown) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    body()
                }
            }
        }
    }
}

@Composable
internal fun EmptyLine(q: String) {
    Text(
        if (q.isEmpty()) "Nothing here yet." else "Nothing matches \"$q\".",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun AppIcon(app: AppInfo, modifier: Modifier = Modifier) {
    val image = remember(app.icon) {
        // A 96 px icon is a few KB; anything far bigger is a TV sending something odd.
        app.icon?.takeIf { it.size <= 64 * 1024 }?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    if (image != null) {
        Image(image, contentDescription = null, modifier.size(40.dp).clip(MaterialTheme.shapes.medium))
        return
    }
    val bg = app.iconColor?.let { Color(it) } ?: MaterialTheme.colorScheme.secondaryContainer
    val fg = if (app.iconColor != null) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
    Box(modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(bg), contentAlignment = Alignment.Center) {
        Text(app.label.take(1).uppercase(), color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

/** The name of the home screen [app] would replace, or null when it isn't offered as one. */
private fun homeLabel(apps: List<AppInfo>, app: AppInfo): String? =
    app.homeInstead?.let { pkg -> apps.firstOrNull { it.packageName == pkg }?.label }

/** Uninstall has no undo, so it asks in a dialog that names the app, not inline. */
@Composable
fun UninstallDialog(app: AppInfo, onUninstall: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Copy.uninstallTitle(app.label)) },
        text = { Text(Copy.explain(Action.Uninstall, app.label, app.packageName)) },
        confirmButton = {
            Button(
                onClick = onUninstall,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text(Copy.confirm(Action.Uninstall)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppRow(
    app: AppInfo,
    procs: List<Proc>,
    pending: Pair<String, Action>?,
    busy: Boolean,
    actions: DeviceActions,
    /** The current home screen's name, when this app can replace it. */
    homeLabel: String? = null,
) {
    var open by rememberSaveable(app.packageName) { mutableStateOf(false) }
    // A launcher can be protected by its name and still be offered as the new home screen.
    val home = app.homeInstead?.takeIf { app.enabled && homeLabel != null }
    val changeable = app.protectedReason == null || home != null
    val ram = procs.filter { it.packageName == app.packageName }.sumOf { it.ramMb }
    val status = when {
        !app.enabled -> "Disabled"
        ram > 0 -> "Running"
        else -> "Not running"
    }
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(enabled = changeable) { open = !open }.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, Modifier.alpha(if (app.enabled) 1f else 0.4f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    app.protectedReason?.let { "Protected: $it" } ?: status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (app.protectedReason != null) {
                Icon(Icons.Default.Lock, contentDescription = "Protected", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (busy) androidx.compose.material3.LoadingIndicator(Modifier.size(32.dp))
        }
        val proposed = pending?.takeIf { it.first == app.packageName }?.second
        AnimatedVisibility((open || proposed != null) && changeable) {
            Column(Modifier.padding(start = 52.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // The label is whatever the app calls itself; the package id is what an action touches.
                Text(app.packageName, style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (proposed == null) {
                    if (app.protectedReason == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (app.enabled && ram > 0) OutlinedButton(onClick = { actions.propose(app.packageName, Action.Stop) }) { Text(Copy.button(Action.Stop)) }
                        if (app.enabled) OutlinedButton(onClick = { actions.propose(app.packageName, Action.Disable) }) { Text(Copy.button(Action.Disable)) }
                        else FilledTonalButton(onClick = { actions.propose(app.packageName, Action.Enable) }) { Text(Copy.button(Action.Enable)) }
                        if (app.removable) OutlinedButton(onClick = { actions.propose(app.packageName, Action.Uninstall) }) { Text(Copy.button(Action.Uninstall)) }
                    }
                    if (home != null) OutlinedButton(onClick = { actions.propose(app.packageName, Action.Home) }) { Text(Copy.MAKE_HOME) }
                } else if (proposed == Action.Uninstall) {
                    UninstallDialog(app, onUninstall = actions.confirm, onDismiss = actions.cancel)
                } else {
                    Text(
                        if (proposed == Action.Home && home != null) Copy.explainHome(app.label, app.packageName, homeLabel!!, home)
                        else Copy.explain(proposed, app.label, app.packageName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = actions.confirm) { Text(Copy.confirm(proposed)) }
                        TextButton(onClick = actions.cancel) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryList(
    history: List<HistoryEntry>,
    now: Long,
    onUndo: (HistoryEntry) -> Unit,
    restoring: Boolean = false,
    onRestoreAll: () -> Unit = {},
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val off = HistoryBook.leftDisabled(history).size
    if (confirming) RestoreDialog(off, onRestore = { confirming = false; onRestoreAll() }, onDismiss = { confirming = false })
    var disabling by remember { mutableStateOf<HistoryEntry?>(null) }
    disabling?.let { h ->
        UndoDisableDialog(h, onDisable = { disabling = null; onUndo(h) }, onDismiss = { disabling = null })
    }
    if (off > 0) {
        OutlinedButton(onClick = { confirming = true }, enabled = !restoring) {
            Text(if (restoring) "Turning apps back on…" else "Restore everything ($off)")
        }
    }
    if (history.isEmpty()) {
        Text(
            "Nothing changed yet. Anything you stop, disable or turn on shows up here, with a way to undo it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    history.forEach { h ->
        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Copy.done(h.action, h.label), style = MaterialTheme.typography.bodyMedium)
                Text(Copy.ago(h.at, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                h.undoneAt != null -> Text("Undone", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                h.undo != null -> TextButton(onClick = { if (h.undo == Action.Disable) disabling = h else onUndo(h) }) { Text(Copy.undo(h.undo)) }
            }
        }
    }
}
