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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import app.tellyscope.data.NetworkInfo
import app.tellyscope.data.Proc
import app.tellyscope.data.Snapshot
import app.tellyscope.data.Suggestions
import app.tellyscope.ui.theme.SmallNumber
import app.tellyscope.ui.theme.StatNumber

data class DeviceActions(
    val select: (String) -> Unit = {},
    val addDevice: () -> Unit = {},
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
    val pickList: (String) -> Unit = {},
    val disableSuggested: (List<String>) -> Unit = {},
    val remote: () -> Unit = {},
)

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
                            placeholder = { Text("Search apps, processes, connections") },
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
                    title = { DevicePicker(state, actions) },
                    actions = {
                        TextButton(onClick = actions.remote, enabled = !offline) { Text("Remote") }
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
            item {
                Text(
                    listOfNotNull(device.model, device.android?.let { "Android $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Details(
                    listOfNotNull(
                        "IP address" to device.hit.ip,
                        "Port" to device.hit.port.toString(),
                        (device.manufacturer ?: device.hit.maker)?.let { "Maker" to it },
                        device.hit.hostname?.let { "Network name" to it },
                    ),
                )
            }
            val restart = state.restarting[device.id]
            if (restart != null) {
                item { RestartingCard(restart, state.now) }
            } else if (device.state == DeviceState.NotAllowed) {
                item { NotAllowedCard(actions.reconnect) }
            } else if (offline) {
                item { OfflineCard(device, state.now, actions.reconnect) }
            }
            val snap = ui.snapshot
            if (snap != null) {
                if (!offline) item { HealthLine(Copy.health(snap, heaviest(snap, ui.apps))) }
                // Offline: the last stats this phone saw, greyed and dated, rather than a grid of blanks.
                item { Stats(snap, if (offline) state.now else null) }
            } else if (offline) {
                item {
                    Text(
                        Copy.noStats(device.state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!offline && q.isEmpty()) item { SendRow(actions) }
            snap?.let {
                // The :adb source has no per-process list yet; no bars beats an empty card.
                if (q.isEmpty() && it.procs.isNotEmpty() && !offline) item { UsageCard(it, ui.apps) }
            }
            if (!offline && q.isEmpty()) {
                val count = (ui.lists as? Suggestions.Ready)?.groups?.sumOf { it.packages.size } ?: 0
                section("lists", "Suggested for this TV", count) {
                    SuggestionsBlock(ui.lists, ui.busy == DISABLING, actions.pickList, actions.disableSuggested)
                }
            }
            // An offline TV can't list anything: sections with nothing in them stay out of the way.
            if (!offline || ui.apps.isNotEmpty()) section(
                key = "apps",
                title = "Apps",
                count = ui.apps.size,
                expandedByDefault = true,
                forceOpen = q.isNotEmpty(),
            ) {
                val apps = ui.apps
                    .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.contains(q) }
                    .sortedWith(compareBy({ it.protectedReason != null }, { !it.enabled }, { it.system }, { it.label }))
                if (apps.isEmpty()) EmptyLine(q)
                apps.forEach { app ->
                    AppRow(app, snap?.procs.orEmpty(), ui.pending, ui.busy == app.packageName, actions)
                }
            }
            if (!offline || !snap?.procs.isNullOrEmpty()) section("procs", "Processes", snap?.procs?.size ?: 0, forceOpen = q.isNotEmpty()) {
                ProcessTable(snap?.procs.orEmpty(), ui.apps, q)
            }
            if (!offline || ui.network != null) section("net", "Network", ui.network?.sockets?.size ?: 0, forceOpen = q.isNotEmpty()) {
                ui.network?.let { NetworkBlock(it, q) }
            }
            if (!offline && q.isEmpty()) section("files", "Files on the TV", ui.files.list?.size ?: 0) {
                FilesBlock(ui.files, state.now, actions)
            }
            section("history", "History", ui.history.size) {
                HistoryList(ui.history, state.now, actions.undo, restoring = ui.busy == RESTORING, onRestoreAll = actions.restoreAll)
            }
        }
        ui.sideload?.let { SideloadDialog(it, actions) }
        ui.transfer?.let { TransferDialog(it, actions.cancelTransfer) }
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
            Modifier.clip(MaterialTheme.shapes.large).clickable { open = true }.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandBadge(device)
            Column(Modifier.weight(1f, fill = false)) {
                Text(name(device), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StateDot(device.state)
                    Spacer(Modifier.width(6.dp))
                    Text(Copy.state(device.state), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch TV")
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            state.devices
                .filter { state.showOffline || it.state == DeviceState.Online || it.id == state.selectedId }
                .forEach { d ->
                    DropdownMenuItem(
                        text = { Text(name(d)) },
                        leadingIcon = { StateDot(d.state) },
                        trailingIcon = {
                            Text(
                                if (d.state == DeviceState.Online) Copy.state(d.state)
                                else d.lastSeen?.let { "${Copy.state(d.state)}, seen ${Copy.ago(it, state.now)}" } ?: Copy.state(d.state),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
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
                text = { Text("About & licences") },
                leadingIcon = { Icon(Icons.Default.Info, null) },
                onClick = { open = false; about = true },
            )
        }
    }
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

/**
 * The maker's name on a tile, when the TV said who made it. A wordmark in the app's own colours,
 * not the maker's logo: no third-party artwork ships in the app.
 */
@Composable
private fun BrandBadge(d: Device) {
    val maker = (d.manufacturer ?: d.hit.maker)?.let(::brand) ?: return
    Box(
        Modifier.padding(end = 10.dp).widthIn(min = 52.dp).height(36.dp).clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(maker, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
    }
}

/** Makers of Android TVs and boxes, as they write their own name. Chip makers (Amlogic) don't count. */
private fun brand(maker: String): String? {
    val words = maker.split(Regex("[^A-Za-z]+"))
    return BRANDS.firstOrNull { b -> words.any { it.equals(b, ignoreCase = true) } }
}

private val BRANDS = listOf(
    "XGIMI", "Google", "Sony", "TCL", "Xiaomi", "Philips", "Hisense", "NVIDIA", "Amazon", "Nokia",
    "Toshiba", "Sharp", "Skyworth", "Haier", "Panasonic", "Nebula", "Dangbei", "Formovie", "onn",
)

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

/**
 * Memory and storage as "75% used" with a bar, then the small stats. Missing stats show "—",
 * never 0. [staleSince] set means the TV is offline: these are its last numbers, greyed and dated.
 */
@Composable
private fun Stats(s: Snapshot, staleSince: Long?) {
    val cells = listOf(
        "CPU" to "${s.cpuPercent}%",
        "Temperature" to s.tempC?.let { "$it°C" },
        "On for" to Copy.uptime(s.uptimeSeconds),
        "Screen" to s.screenOn?.let { if (it) "On" else "Off" },
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (staleSince != null) {
                Text(
                    "As of ${Copy.ago(s.at, staleSince)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.alpha(if (staleSince != null) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Meter("Memory", s.ramUsedMb / 1024.0, s.ramTotalMb / 1024.0)
                s.storageUsedGb?.let { u -> s.storageTotalGb?.let { t -> Meter("Storage", u, t) } }
                Row(Modifier.fillMaxWidth()) {
                    cells.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value ?: "—", style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Meter(label: String, usedGb: Double, totalGb: Double) {
    val frac = if (totalGb > 0) (usedGb / totalGb).toFloat().coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(frac * 100).toInt()}% used", style = StatNumber)
            }
            Text(Copy.gbOf(usedGb, totalGb), style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { frac },
            Modifier.fillMaxWidth(),
            color = if (frac >= Copy.FULL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

/** Grey bars, no chart library: top 6 by value, the rest summed as "other". */
@Composable
private fun UsageCard(snap: Snapshot, apps: List<AppInfo>) {
    val labels = apps.associate { it.packageName to it.label }
    fun label(p: Proc) = p.packageName?.let { labels[it] ?: AppNames.fallback(it) } ?: Copy.ANDROID_SYSTEM
    val byApp = snap.procs.groupBy(::label)
    var cpu by rememberSaveable { mutableStateOf(false) }
    val rows = byApp.map { (name, ps) ->
        name to if (cpu) ps.sumOf { it.cpuPercent } else ps.sumOf { it.ramMb }.toDouble()
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (cpu) "CPU by app" else "RAM by app", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { cpu = !cpu }) { Text(if (cpu) "Show RAM" else "Show CPU") }
            }
            Bars(rows, fmt = { if (cpu) "%.0f%%".format(it) else Copy.mb(it.toInt()) })
        }
    }
}

@Composable
private fun Bars(rows: List<Pair<String, Double>>, fmt: (Double) -> String, top: Int = 6) {
    val sorted = rows.sortedByDescending { it.second }
    val rest = sorted.drop(top).sumOf { it.second }
    val shown = sorted.take(top) + if (rest > 0) listOf("Other" to rest) else emptyList()
    val max = shown.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
    shown.forEach { (name, v) ->
        val frac by animateFloatAsState((v / max).toFloat(), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "bar")
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Long app names wrap to two lines rather than cut off; the value column only needs "1.2 GB".
            Text(name, Modifier.width(148.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(12.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary))
            }
            Text(fmt(v), Modifier.width(60.dp), style = SmallNumber, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
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
private fun EmptyLine(q: String) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppRow(
    app: AppInfo,
    procs: List<Proc>,
    pending: Pair<String, Action>?,
    busy: Boolean,
    actions: DeviceActions,
) {
    var open by rememberSaveable(app.packageName) { mutableStateOf(false) }
    val ram = procs.filter { it.packageName == app.packageName }.sumOf { it.ramMb }
    val status = when {
        !app.enabled -> "Disabled"
        ram > 0 -> "Running · ${Copy.mb(ram)}"
        else -> "Not running"
    }
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(enabled = app.protectedReason == null) { open = !open }.padding(vertical = 8.dp)) {
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
        AnimatedVisibility((open || proposed != null) && app.protectedReason == null) {
            Column(Modifier.padding(start = 52.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // The label is whatever the app calls itself; the package id is what an action touches.
                Text(app.packageName, style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (proposed == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (app.enabled && ram > 0) OutlinedButton(onClick = { actions.propose(app.packageName, Action.Stop) }) { Text(Copy.button(Action.Stop)) }
                        if (app.enabled) OutlinedButton(onClick = { actions.propose(app.packageName, Action.Disable) }) { Text(Copy.button(Action.Disable)) }
                        else FilledTonalButton(onClick = { actions.propose(app.packageName, Action.Enable) }) { Text(Copy.button(Action.Enable)) }
                    }
                } else {
                    Text(Copy.explain(proposed, app.label, app.packageName), style = MaterialTheme.typography.bodyMedium)
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
private fun ProcessTable(procs: List<Proc>, apps: List<AppInfo>, q: String) {
    val labels = apps.associate { it.packageName to it.label }
    val groups = procs
        .groupBy { p -> p.packageName?.let { labels[it] ?: AppNames.fallback(it) } ?: Copy.ANDROID_SYSTEM }
        .filter { (name, ps) -> q.isEmpty() || name.lowercase().contains(q) || ps.any { it.name.lowercase().contains(q) } }
        .toList()
        .sortedByDescending { (_, ps) -> ps.sumOf { it.cpuPercent } }
    if (groups.isEmpty()) return EmptyLine(q)
    Row {
        Text("App", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("CPU", Modifier.width(56.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Text("RAM", Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    groups.forEach { (name, ps) ->
        var open by rememberSaveable(name) { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp)) {
            Row {
                Text(
                    if (ps.size > 1) "$name  ×${ps.size}" else name,
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("%.0f%%".format(ps.sumOf { it.cpuPercent }), Modifier.width(56.dp), style = SmallNumber, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Text(Copy.mb(ps.sumOf { it.ramMb }), Modifier.width(72.dp), style = SmallNumber, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            if (open) ps.forEach { p ->
                Row(Modifier.padding(start = 12.dp, top = 2.dp)) {
                    Text("${p.pid}  ${p.name}", Modifier.weight(1f), style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("%.1f%%".format(p.cpuPercent), Modifier.width(56.dp), style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Text(Copy.mb(p.ramMb), Modifier.width(72.dp), style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun NetworkBlock(n: NetworkInfo, q: String) {
    if (q.isEmpty()) {
        KeyValue("Connection", listOfNotNull(if (n.iface == "wifi") "Wi-Fi" else "Ethernet", n.ssid).joinToString(" · "))
        n.rssi?.let { r -> KeyValue("Signal", signalWord(r).replaceFirstChar(Char::uppercase)) }
        Details(
            listOfNotNull(
                "IP address" to n.ip,
                n.mac?.let { "MAC" to it },
                n.rssi?.let { "Signal" to "$it dBm" },
                n.linkMbps?.let { "Link speed" to "$it Mbps" },
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text("Data used", style = MaterialTheme.typography.titleSmall)
        Bars(n.traffic.map { it.app to (it.rxMb + it.txMb).toDouble() }, fmt = { Copy.mb(it.toInt()) })
        Spacer(Modifier.height(8.dp))
    }
    Text("Open connections", style = MaterialTheme.typography.titleSmall)
    val socks = n.sockets.filter { q.isEmpty() || it.app.lowercase().contains(q) || it.remote.lowercase().contains(q) }
    if (socks.isEmpty()) EmptyLine(q)
    socks.forEach { s ->
        Row(Modifier.padding(vertical = 2.dp)) {
            Text(s.app, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.remote, style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun signalWord(rssi: Int) = when {
    rssi >= -55 -> "strong"
    rssi >= -67 -> "good"
    rssi >= -75 -> "weak"
    else -> "very weak"
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
                h.undo != null -> TextButton(onClick = { onUndo(h) }) { Text(Copy.undo(h.undo)) }
            }
        }
    }
}
