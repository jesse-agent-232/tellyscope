// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tellyscope.data.ScanHit
import app.tellyscope.data.ScanKind
import app.tellyscope.ui.theme.SmallNumber
import app.tellyscope.ui.theme.StatNumber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScanScreen(
    scan: ScanUi,
    now: Long,
    onRescan: () -> Unit,
    onConnect: (ScanHit) -> Unit,
    onCode: (ScanHit, String) -> Unit,
    onPair: (ScanHit) -> Unit,
    onAddByIp: (String) -> String?,
    onBack: (() -> Unit)? = null,
    onOpenWifi: () -> Unit = {},
) {
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tellyscope") },
                navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("Done") } },
                actions = {
                    IconButton(onClick = onRescan, enabled = !scan.scanning) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan again")
                    }
                },
            )
        },
    ) { pad ->
        if (scan.offWifi) {
            OffWifi(Modifier.padding(pad), onOpenWifi, onAddByIp = { adding = true })
        } else LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("What's running on your TV?", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Live stats, apps and network for the Android TVs on your Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { ScanStatus(scan) }
            items(scan.hits, key = { it.ip }) { hit ->
                HitCard(hit, scan.rows[hit.ip] ?: RowState.Found, now, onConnect, onCode, onPair)
            }
            if (!scan.scanning && scan.hits.isEmpty()) {
                item { GuideCard() }
            }
            item {
                OutlinedButton(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add by IP address")
                }
            }
        }
    }
    if (adding) AddByIpDialog(subnet = scan.progress?.subnet, onDismiss = { adding = false }, onSubmit = { onAddByIp(it).also { e -> if (e == null) adding = false } })
}

/** Off Wi-Fi with no TV announcing itself: one way forward instead of an empty list. */
@Composable
private fun OffWifi(modifier: Modifier, onOpenWifi: () -> Unit, onAddByIp: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(Modifier.size(112.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Icon(DeviceIcons.WifiOff, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("This phone isn't on Wi-Fi", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            "Join the same Wi-Fi as your TV. Tellyscope looks for it again as soon as you're on.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onOpenWifi) { Text("Open Wi-Fi settings") }
        // VPNs and odd setups can still reach a TV by its address.
        TextButton(onClick = onAddByIp) { Text("Add by IP address") }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanStatus(scan: ScanUi) {
    val p = scan.progress
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (scan.scanning) {
            Text(
                "Looking for TVs${if (scan.noLan) "" else " on your Wi-Fi"}…  ${p?.probed ?: 0}/${p?.total ?: 254}",
                style = MaterialTheme.typography.labelLarge,
            )
            LinearWavyProgressIndicator(
                progress = { p?.let { it.probed / it.total.toFloat() } ?: 0f },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val n = scan.hits.size
            Text(
                if (n == 0) "No TVs found yet" else "Found $n ${if (n == 1) "TV" else "TVs"}${if (scan.noLan) "" else " on your Wi-Fi"}",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (scan.noLan) {
            Text(
                "You're not on Wi-Fi, so only TVs that announce themselves show up. Join the TV's Wi-Fi, or add it by IP address.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HitCard(
    hit: ScanHit,
    row: RowState,
    now: Long,
    onConnect: (ScanHit) -> Unit,
    onCode: (ScanHit, String) -> Unit,
    onPair: (ScanHit) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(hit.hostname ?: hit.maker ?: "Android device", style = MaterialTheme.typography.titleMedium)
                    Text(
                        hit.maker ?: "Unknown maker",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Details(listOf("IP address" to hit.ip, "Port" to hit.port.toString()))
                }
                when (row) {
                    RowState.Found -> FilledTonalButton(onClick = { onConnect(hit) }) { Text("Connect") }
                    RowState.NotAllowed -> FilledTonalButton(onClick = { onConnect(hit) }) { Text("Try again") }
                    else -> Unit
                }
            }
            when (row) {
                RowState.Found -> Text(
                    if (hit.kind == ScanKind.Wireless) "Needs the pairing code from the TV" else "Ready to connect",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is RowState.Waiting -> if (hit.kind == ScanKind.Wireless) Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Connecting…", style = MaterialTheme.typography.bodyMedium)
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Check your TV", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "A box saying \"Allow USB debugging?\" is on screen. Tick \"Always allow from this computer\", then press Allow.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Waiting… ${((now - row.since) / 1000).coerceAtLeast(0)} s",
                            style = SmallNumber,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                RowState.NotAllowed -> Column {
                    Text("No answer from the TV", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    Text(
                        "Is the box still on screen? If you pressed Deny by mistake, tap Try again and it will ask once more.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is RowState.Code -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "On the TV: Settings → Developer options → Wireless debugging → Pair device with pairing code. Type the 6 digits it shows:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    CodeBoxes(row.input, onChange = { onCode(hit, it) }, enabled = !row.busy, isError = row.error != null)
                    val missing = CODE_LENGTH - row.input.length
                    // The button says what's missing instead of sitting there disabled with no reason.
                    Button(onClick = { onPair(hit) }, enabled = !row.busy && missing == 0) {
                        Text(
                            when {
                                row.busy -> "Pairing…"
                                missing == 0 -> "Pair"
                                missing == 1 -> "1 more digit"
                                else -> "$missing more digits"
                            },
                        )
                    }
                    row.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    row.detail?.let { Details(listOf("From the TV" to it)) }
                }
            }
        }
    }
}

private const val CODE_LENGTH = 6

/** One box per digit, on the number keypad. A single hidden field underneath, so paste and delete just work. */
@Composable
private fun CodeBoxes(code: String, onChange: (String) -> Unit, enabled: Boolean, isError: Boolean) {
    BasicTextField(
        value = code,
        onValueChange = onChange,
        modifier = Modifier.semantics { contentDescription = "Pairing code" },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = { _ ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(CODE_LENGTH) { i ->
                    val current = enabled && i == code.length
                    val color = when {
                        isError -> MaterialTheme.colorScheme.error
                        current -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Box(
                        Modifier.size(width = 44.dp, height = 56.dp)
                            .border(if (current) 2.dp else 1.dp, color, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(code.getOrNull(i)?.toString().orEmpty(), style = StatNumber)
                    }
                }
            }
        },
    )
}

@Composable
private fun AddByIpDialog(subnet: String?, onDismiss: () -> Unit, onSubmit: (String) -> String?) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add by IP address") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Find it on the TV under Settings → Network → your Wi-Fi.")
                // The scan screen says "your Wi-Fi"; the range is only useful here, while typing.
                if (subnet != null) Text("Your Wi-Fi: $subnet", style = SmallNumber, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = { Button(onClick = { error = onSubmit(text) }) { Text("Connect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
