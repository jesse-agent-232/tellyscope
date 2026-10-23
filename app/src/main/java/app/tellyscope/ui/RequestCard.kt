// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.tellyscope.adb.ConnectionRequest

/**
 * The TV's "Allow debugging?" prompt, answered from the phone. The fingerprint is always shown,
 * "Always allow" starts unticked for every new request, and Deny comes first.
 */
@Composable
fun RequestCard(request: ConnectionRequest, busy: Boolean, answer: (allow: Boolean, always: Boolean) -> Unit) {
    var always by rememberSaveable(request.fingerprint) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Copy.REQUEST_TITLE, style = MaterialTheme.typography.titleMedium)
            Text(Copy.REQUEST_TEXT, style = MaterialTheme.typography.bodyMedium)
            // Eight pairs a line, so a pair is never split when comparing it with the other device.
            Text(request.fingerprint.split(':').chunked(8).joinToString("\n") { it.joinToString(":") }, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            Row(
                Modifier.fillMaxWidth().toggleable(always, enabled = !busy, role = Role.Checkbox) { always = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = always, onCheckedChange = null, enabled = !busy)
                Text(Copy.REQUEST_ALWAYS, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { answer(false, false) }, enabled = !busy) { Text(Copy.REQUEST_DENY) }
                OutlinedButton(onClick = { answer(true, always) }, enabled = !busy) { Text(Copy.REQUEST_ALLOW) }
            }
        }
    }
}
