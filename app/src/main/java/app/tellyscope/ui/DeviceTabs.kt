// SPDX-License-Identifier: GPL-3.0-only
// Apps, Files and Tweaks icons are the Material Icons "apps", "folder" and "tune", and Upload is
// "upload" (Apache-2.0), see NOTICE.

package app.tellyscope.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.tellyscope.data.Action
import app.tellyscope.data.Device
import app.tellyscope.data.DeviceState
import app.tellyscope.data.Snapshot
import app.tellyscope.data.Suggestions
import app.tellyscope.data.deviceKind

/** The TV page's destinations, one job each, in the bottom bar. */
enum class TvTab(val label: String) { Overview("Overview"), Apps("Apps"), Files("Files"), Tweaks("Tweaks") }

private object TabIcons {
    val Apps = icon(
        "Apps",
        "M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z",
    )
    val Folder = icon("Folder", "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z")
    val Tune = icon(
        "Tune",
        "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z",
    )

    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), pathFillType = PathFillType.NonZero, fill = SolidColor(Color.Black))
        .build()
}

private fun TvTab.icon() = when (this) {
    TvTab.Overview -> Icons.Default.Home
    TvTab.Apps -> TabIcons.Apps
    TvTab.Files -> TabIcons.Folder
    TvTab.Tweaks -> TabIcons.Tune
}

/**
 * The TV page as four tabs under a bottom bar, in place of one long scroll of folded sections.
 * Overview leads with the TV itself, big and centred, and a chip per TV to switch; the other tabs
 * name the TV in the app bar. Search (Apps only) and the overflow sit on the right.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeviceTabsScreen(
    state: UiState,
    actions: DeviceActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    startTab: TvTab = TvTab.Overview,
) {
    val device = state.selected ?: return
    val ui = state.device
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var onlyLocked by rememberSaveable { mutableStateOf(false) }
    val q = state.query.trim().lowercase()
    val offline = device.state != DeviceState.Online
    val list = rememberLazyListState()
    // "See changes" lands on Tweaks scrolled to Recent changes, the list's second item.
    var toChanges by remember { mutableStateOf(false) }
    LaunchedEffect(tab, toChanges) {
        if (tab == TvTab.Tweaks && toChanges) { list.scrollToItem(1); toChanges = false }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        // Each tab's main action as Material's extended FAB: install on Apps, upload on Files.
        floatingActionButton = {
            if (!offline) when (tab) {
                TvTab.Apps -> ExtendedFloatingActionButton(onClick = actions.pickApp, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Install app") })
                TvTab.Files -> ExtendedFloatingActionButton(onClick = { actions.pickFile(ui.files.folder) }, icon = { Icon(TileIcons.Upload, null) }, text = { Text("Upload files") })
                else -> {}
            }
        },
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
                    // Overview shows the TV big below; the bar says which TV only on the other tabs.
                    title = { if (tab != TvTab.Overview) BarName(state) },
                    actions = {
                        if (REMOTE_ENABLED) TextButton(onClick = actions.remote, enabled = !offline) { Text("Remote") }
                        // Search only looks through apps, so it only shows on the Apps tab.
                        if (tab == TvTab.Apps) {
                            IconButton(onClick = { searching = true }, enabled = !offline) {
                                Icon(Icons.Default.Search, contentDescription = "Search apps")
                            }
                        }
                        AppMenu(state, actions)
                    },
                )
            }
        },
        bottomBar = {
            ShortNavigationBar {
                TvTab.entries.forEach { t ->
                    ShortNavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            // Leaving Apps closes the search, so the list isn't still filtered on return.
                            if (t != TvTab.Apps && searching) { searching = false; actions.query("") }
                            tab = t
                        },
                        icon = { Icon(t.icon(), contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            state = list,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val snap = ui.snapshot
            val live = snap?.takeIf { !offline }
            when (tab) {
                TvTab.Overview -> {
                    item { Hero(state, live, actions) }
                    val restart = state.restarting[device.id]
                    if (restart != null) {
                        item { RestartingCard(restart, state.now) }
                    } else if (device.state == DeviceState.NotAllowed) {
                        item { NotAllowedCard(actions.reconnect) }
                    } else if (offline) {
                        item { OfflineCard(device, state.now, actions.reconnect) }
                    }
                    // Away, storage is the last one seen, this session's or the saved one after a restart.
                    val used = snap?.storageUsedGb ?: device.lastKnown?.storageUsedGb
                    val total = snap?.storageTotalGb ?: device.lastKnown?.storageTotalGb
                    if (live != null) {
                        item { SystemWidget(live) }
                    } else if (offline && used != null && total != null) {
                        item { SystemWidget(used, total, null, asOf = device.lastSeen?.let { Copy.ago(it, state.now) } ?: "the last visit") }
                    } else if (offline) {
                        item { Muted(Copy.noStats(device.state)) }
                    }
                    val debloat = debloatState(ui)
                    if (!offline) {
                        item {
                            ActionTiles(
                                actions,
                                count = debloat.count,
                                todo = debloat.todo,
                                disabling = ui.busy == DISABLING,
                                // With nothing left on the list, Debloat leads to the apps for one-off changes.
                                onDebloat = if (debloat.todo) actions.debloat else ({ tab = TvTab.Apps }),
                            )
                        }
                    }
                    item { SectionTitle("Suggested for this TV") }
                    item { SuggestedCard(ui.lists, debloat, offline, onChanges = { toChanges = true; tab = TvTab.Tweaks }) }
                    item { AndroidCard(state, snap, offline, onUpdates = actions.checkUpdates) }
                }
                TvTab.Apps -> {
                    val locked = ui.apps.count { it.protectedReason != null }
                    item(key = "filter") { AppsFilter(ui.apps.size - locked, locked, onlyLocked) { onlyLocked = it } }
                    val shown = ui.apps
                        .filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.contains(q) }
                        // A search looks through every app; the switch only narrows the plain list.
                        .filter { q.isNotEmpty() || (it.protectedReason != null) == onlyLocked }
                        .sortedWith(compareBy({ !it.enabled }, { it.system }, { it.label }))
                    if (shown.isEmpty()) item { EmptyLine(q) }
                    items(shown, key = { it.packageName }) { app ->
                        AppRow(app, snap?.procs.orEmpty(), ui.pending, ui.busy == app.packageName, actions, homeLabel(ui.apps, app))
                    }
                }
                TvTab.Files -> {
                    if (offline) item { Muted("Connect to the TV to see its files.") }
                    else {
                        item { FilesBlock(ui.files, state.now, actions) }
                    }
                }
                TvTab.Tweaks -> {
                    if (offline) item { Muted("Connect to the TV to change its settings.") }
                    else item { Panel(Copy.TWEAKS) { TweaksBlock(ui, actions) } }
                    item {
                        Panel("Recent changes") {
                            HistoryList(ui.history, state.now, actions.undo, restoring = ui.busy == RESTORING, onRestoreAll = actions.restoreAll)
                        }
                    }
                }
            }
        }
        if (!offline) ui.request?.let { RequestDialog(it, ui.busy == ANSWERING, actions.answerRequest, actions.dismissRequest) }
        ui.sideload?.let { SideloadDialog(it, actions) }
        ui.transfer?.let { TransferDialog(it, actions.cancelTransfer) }
    }
}

/** Apps the user can change by default; the switch's other side shows the ones Tellyscope protects. */
@Composable
private fun AppsFilter(changeable: Int, locked: Int, onlyLocked: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf(false to "Changeable ($changeable)", true to "${Copy.CANT_CHANGE} ($locked)").forEachIndexed { i, (side, label) ->
            SegmentedButton(
                selected = onlyLocked == side,
                onClick = { onChange(side) },
                shape = SegmentedButtonDefaults.itemShape(i, 2),
            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** A titled card for a tab's block, not folded: each tab has room for it. */
@Composable
private fun Panel(title: String, body: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            body()
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The overflow: this TV's own items first, named so it's clear which TV they touch, then the app-wide ones. */
@Composable
private fun AppMenu(state: UiState, actions: DeviceActions) {
    var open by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val device = state.selected ?: return
    if (about) AboutDialog(
        when (val l = state.device.lists) {
            is Suggestions.NoList -> l.commit
            is Suggestions.Ready -> l.commit
            else -> null
        },
        onDismiss = { about = false },
    )
    if (forgetting) ForgetDialog(name(device), onForget = { forgetting = false; actions.forget(device.id) }, onDismiss = { forgetting = false })
    if (renaming) RenameDialog(device.nickname.orEmpty(), device.model ?: networkName(device), onSave = { renaming = false; actions.rename(device.id, it) }, onDismiss = { renaming = false })
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Text(name(device), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            DropdownMenuItem(text = { Text("Rename this TV") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { open = false; renaming = true })
            DropdownMenuItem(text = { Text("Forget this TV") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { open = false; forgetting = true })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Advanced") }, leadingIcon = { Icon(Icons.Default.List, null) }, onClick = { open = false; actions.advanced() })
            DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { open = false; actions.settings() })
            DropdownMenuItem(text = { Text("About & licences") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { open = false; about = true })
        }
    }
}

/** The bar on every tab but Overview: which TV, nothing to open. Switching happens on Overview. */
@Composable
private fun BarName(state: UiState) {
    val device = state.selected ?: return
    val kind = deviceKind(device)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(DeviceIcons.of(kind), contentDescription = kind.word(), Modifier.padding(end = 12.dp))
        Text(name(device), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
/** The TV as Overview's hero: an Expressive cookie-shaped tile, its name, how it's doing, and the switcher. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Hero(state: UiState, live: Snapshot?, actions: DeviceActions) {
    val device = state.selected ?: return
    val kind = deviceKind(device)
    val c = MaterialTheme.colorScheme
    // Offline: the tile greys out, so it agrees with the Offline pill.
    val on = device.state == DeviceState.Online
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(80.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(if (on) c.primaryContainer else c.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(DeviceIcons.of(kind), contentDescription = kind.word(), Modifier.size(40.dp), tint = if (on) c.onPrimaryContainer else c.outline)
        }
        val title = name(device)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
            // Under a model-name title, the model would only say it twice.
            val maker = device.manufacturer ?: device.hit.maker
            val made = if (title == device.model) maker.orEmpty() else made(maker, device.model)
            if (made.isNotEmpty()) Text(made, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
        val health = live?.let { Copy.health(it, heaviest(it, state.device.apps)) }
        Surface(shape = CircleShape, color = if (health?.ok == false) c.errorContainer else c.secondaryContainer) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (health != null) {
                    Icon(if (health.ok) Icons.Default.CheckCircle else Icons.Default.Warning, null, Modifier.size(16.dp), tint = if (health.ok) c.primary else c.error)
                } else StateDot(device.state)
                Spacer(Modifier.width(8.dp))
                Text(health?.text ?: Copy.state(device.state), style = MaterialTheme.typography.labelLarge)
            }
        }
        TvChips(state, actions)
    }
}

/** One chip per TV to switch, offline ones dimmed, then "+ Device" as the last chip. */
@Composable
private fun TvChips(state: UiState, actions: DeviceActions) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.devices.size > 1) state.devices.forEach { d ->
            FilterChip(
                selected = d.id == state.selectedId,
                onClick = { actions.select(d.id) },
                label = { Text(name(d), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 104.dp)) },
                leadingIcon = {
                    Box {
                        Icon(DeviceIcons.of(deviceKind(d)), contentDescription = deviceKind(d).word(), Modifier.size(18.dp))
                        Box(Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp)) { StateDot(d.state) }
                    }
                },
                modifier = if (d.state == DeviceState.Online) Modifier else Modifier.alpha(0.6f),
            )
        }
        // Adding a TV sits with the TVs, as the last chip.
        AssistChip(onClick = actions.addDevice, label = { Text("Device") }, leadingIcon = { Icon(Icons.Default.Add, contentDescription = "Add a", Modifier.size(18.dp)) })
    }
}

/** Where the suggested list stands on this TV, worked out from the live app list. */
internal data class DebloatState(
    /** Apps on the list that are installed here and Tellyscope may turn off, on or off. */
    val total: Int,
    /** Of those, the ones still on. */
    val on: Int,
    /** Apps Tellyscope turned off that are on again, often after a TV update. */
    val back: Int,
    /** Apps on the list still on, plus any that came back: the number on the Debloat tile. */
    val count: Int,
    /** Debloat has something to show: apps to turn off, or that this TV has no list. */
    val todo: Boolean,
)

internal fun debloatState(ui: DeviceUi): DebloatState {
    // History lives on this phone, so it only knows what this phone turned off.
    val ours = ui.history.filter { it.action == Action.Disable && it.undoneAt == null }.map { it.packageName }.toSet()
    val back = ui.apps.filter { it.enabled && it.packageName in ours }.map { it.packageName }.toSet()
    val ready = ui.lists as? Suggestions.Ready
    val listed = ready?.list?.let { l -> l.groups.flatMap { g -> g.packages.map { it.pkg } }.filter { it !in l.keep }.toSet() }.orEmpty()
    val candidates = ui.apps.filter { it.packageName in listed && (!it.enabled || it.protectedReason == null) }
    val on = candidates.filter { it.enabled }.map { it.packageName }.toSet()
    val count = (on + back).size
    return DebloatState(candidates.size, on.size, back.size, count, count > 0 || ui.lists is Suggestions.NoList)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 4.dp, top = 4.dp), style = MaterialTheme.typography.titleMedium)
}

/**
 * The list in one line of plain text, with See changes beside it. When apps Tellyscope turned off
 * are on again, one red line says so; the Debloat tile above carries the count and the fix.
 */
@Composable
private fun SuggestedCard(lists: Suggestions?, d: DebloatState, offline: Boolean, onChanges: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val apps = { n: Int -> if (n == 1) "1 app" else "$n apps" }
    Card(colors = CardDefaults.cardColors(containerColor = c.surfaceContainerLow), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (lists) {
                        is Suggestions.Ready -> when {
                            d.total == 0 -> "Nothing from the ${lists.list.name} list is on this TV"
                            d.on == 0 -> if (d.total == 1) "The suggested app is off" else "All ${d.total} suggested apps are off"
                            else -> "${d.total - d.on} of ${d.total} suggested apps are off"
                        }
                        is Suggestions.NoList -> "No list for this TV yet"
                        Suggestions.Unavailable -> "Couldn't load the device lists"
                        null -> if (offline) "Connect to the TV to see its suggestions" else "Looking up this TV's list\u2026"
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton(onClick = onChanges) { Text("See changes") }
            }
            if (d.back > 0) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Action required: ") }
                        append("${apps(d.back)} came back on")
                    },
                    Modifier.padding(end = 8.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodyLarge, color = c.error,
                )
            }
            val note = when {
                lists is Suggestions.Unavailable -> "Check the phone's internet and open this TV again."
                lists is Suggestions.NoList -> "Tap Debloat to pick the apps to turn off by hand."
                lists is Suggestions.Ready && lists.byMaker -> "No list for this exact model, so these come from ${lists.list.name}'s other TVs."
                lists is Suggestions.Ready && !d.todo -> "Debloat now opens Apps for one-off changes."
                else -> null
            }
            note?.let { Text(it, Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant) }
            if (lists is Suggestions.Ready) {
                Text("Lists adapted from cleanyourtv (MIT).", Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
        }
    }
}

/** Which Android and when it last updated, with a way to make the TV look for a newer one. */
@Composable
private fun AndroidCard(state: UiState, snap: Snapshot?, offline: Boolean, onUpdates: () -> Unit) {
    val device = state.selected ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = MaterialTheme.shapes.large) {
        StatusRow(
            Icons.Default.Info,
            device.android?.let { "Android $it" } ?: "Android",
            (snap?.softwareMonth ?: device.lastKnown?.softwareMonth)?.let { "Updated ${Copy.month(it)}" },
            "Check for updates", !offline, onUpdates,
        )
    }
}

@Composable
private fun StatusRow(icon: ImageVector, title: String, sub: String?, button: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = sub?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { TextButton(onClick = onClick, enabled = enabled) { Text(button) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}


private object TileIcons {
    val Upload = icon("Upload", "M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z")

    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), pathFillType = PathFillType.NonZero, fill = SolidColor(Color.Black))
        .build()
}
