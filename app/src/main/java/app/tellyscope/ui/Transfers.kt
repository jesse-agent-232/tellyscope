// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.tellyscope.adb.InstallKind
import app.tellyscope.adb.TvFile
import app.tellyscope.adb.TvFolder
import app.tellyscope.ui.theme.SmallNumber

/**
 * Both dialogs here close only through their own buttons: a tap outside or Back would otherwise
 * drop an install halfway through its questions, or look like it cancelled a copy.
 */
private val Sticky = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)

/** The two ways onto the TV, under the stats. */
@Composable
fun SendRow(actions: DeviceActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = actions.pickApp, Modifier.weight(1f)) {
            Icon(Icons.Default.Add, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Install an app")
        }
        // Where the file lands is the user's choice, every time, not whatever folder was last shown.
        var choosing by remember { mutableStateOf(false) }
        Box(Modifier.weight(1f)) {
            FilledTonalButton(onClick = { choosing = true }, Modifier.fillMaxWidth()) {
                @Suppress("DEPRECATION") // the AutoMirrored set isn't in icons-core
                Icon(Icons.Default.Send, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Send a file")
            }
            DropdownMenu(choosing, onDismissRequest = { choosing = false }) {
                TvFolder.entries.forEach { f ->
                    DropdownMenuItem(
                        text = { Text("To ${Copy.folder(f)}") },
                        onClick = { choosing = false; actions.pickFile(f) },
                    )
                }
            }
        }
    }
}

/** Body of the Files section: the folder switch, then the folder's files, each one tap from the phone. */
@Composable
fun FilesBlock(files: FilesUi, now: Long, actions: DeviceActions) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        TvFolder.entries.forEachIndexed { i, f ->
            SegmentedButton(
                selected = files.folder == f,
                onClick = { actions.folder(f) },
                shape = SegmentedButtonDefaults.itemShape(i, TvFolder.entries.size),
            ) { Text(Copy.folder(f)) }
        }
    }
    Spacer(Modifier.size(4.dp))
    val list = files.list
    when {
        list == null -> Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            LoadingIndicator(Modifier.size(32.dp))
        }
        files.error != null -> Muted(files.error)
        list.isEmpty() -> Muted("Nothing in ${Copy.folder(files.folder)} yet. Files you send land here.")
        else -> list.forEach { f -> FileRow(f, now) { actions.saveFile(f) } }
    }
    Muted("Tap a file to save a copy on your phone.")
}

@Composable
private fun FileRow(f: TvFile, now: Long, onSave: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSave).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(
                "${Copy.bytes(f.size)} · ${Copy.ago(f.modifiedMillis, now)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onSave) { Text("Save") }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SideloadDialog(s: SideloadUi, actions: DeviceActions) {
    val app = s.app
    val kind = s.kind
    AlertDialog(
        onDismissRequest = {},
        properties = Sticky,
        icon = when (s.step) {
            SideloadUi.Step.Installed -> ({ Icon(Icons.Default.CheckCircle, null) })
            SideloadUi.Step.Failed -> ({ Icon(Icons.Default.Warning, null) })
            else -> null
        },
        title = {
            Text(
                when (s.step) {
                    SideloadUi.Step.Reading -> "Reading the app"
                    SideloadUi.Step.Failed -> app?.let { "Couldn't install ${it.label}" } ?: "Can't install that"
                    SideloadUi.Step.Installed -> Copy.installed(kind ?: InstallKind.NEW, app?.label.orEmpty())
                    else -> app?.label.orEmpty()
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.step == SideloadUi.Step.Reading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { LoadingIndicator() }
                }
                if (app != null && s.step != SideloadUi.Step.Failed) {
                    Column {
                        Text(app.info.packageName, style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            listOfNotNull(
                                "Version ${app.info.versionName ?: app.info.versionCode}",
                                Copy.bytes(app.sizeBytes),
                                if (app.files.size > 1) "${app.files.size} parts" else null,
                            ).joinToString(" · "),
                            style = SmallNumber,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when (s.step) {
                    SideloadUi.Step.Ready -> if (kind != null) {
                        Text(Copy.installKind(kind, s.installed?.versionName, app?.info?.versionName), style = MaterialTheme.typography.bodyMedium)
                        if (kind == InstallKind.DOWNGRADE) {
                            Row(
                                Modifier.fillMaxWidth().clickable { actions.allowDowngrade(!s.allowDowngrade) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(s.allowDowngrade, onCheckedChange = null)
                                Spacer(Modifier.width(12.dp))
                                Text(Copy.ALLOW_DOWNGRADE, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    SideloadUi.Step.Installing -> {
                        val f = s.progress?.fraction
                        // Past the last byte the TV is still unpacking: say so rather than sit at 100%.
                        val sending = f != null && f < 1f
                        if (sending) LinearWavyProgressIndicator(progress = { f }, Modifier.fillMaxWidth())
                        else LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            if (sending) "Sending to the TV · ${(f * 100).toInt()}%" else "Installing on the TV…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Muted(Copy.INSTALL_KEEP_OPEN)
                    }
                    SideloadUi.Step.Failed -> {
                        Text(s.error.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        s.detail?.takeIf { it.isNotBlank() }?.let {
                            Text("The TV said: $it", style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (s.step) {
                SideloadUi.Step.Ready -> if (kind != null) Button(
                    onClick = actions.install,
                    enabled = kind != InstallKind.DOWNGRADE || s.allowDowngrade,
                ) { Text(Copy.installButton(kind)) }
                SideloadUi.Step.Installed -> Button(onClick = actions.openInstalled) { Text("Open on TV") }
                SideloadUi.Step.Failed -> Button(onClick = actions.closeSideload) { Text("OK") }
                else -> Unit
            }
        },
        dismissButton = {
            when (s.step) {
                SideloadUi.Step.Reading, SideloadUi.Step.Ready -> TextButton(onClick = actions.closeSideload) { Text("Cancel") }
                SideloadUi.Step.Installed -> TextButton(onClick = actions.closeSideload) { Text("Done") }
                else -> Unit
            }
        },
    )
}

/** A copy in either direction. Cancel is the only way out before it's done. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransferDialog(t: TransferUi, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = Sticky,
        title = { Text(if (t.toTv) "Sending to ${Copy.folder(t.folder)}" else "Saving to your phone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                val f = t.progress?.fraction
                if (f != null) LinearWavyProgressIndicator(progress = { f }, Modifier.fillMaxWidth())
                else LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    t.progress?.let { p -> p.total?.let { "${Copy.bytes(p.done)} of ${Copy.bytes(it)}" } ?: Copy.bytes(p.done) } ?: "Starting…",
                    style = SmallNumber,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
