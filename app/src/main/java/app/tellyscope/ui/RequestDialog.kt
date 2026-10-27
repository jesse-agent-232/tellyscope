// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * The TV's "Allow debugging?" prompt, answered from the phone, as a dialog like the TV's own. The
 * fingerprint is always shown, "Always allow" starts unticked for every new request, and Deny is
 * the safe choice. Dismissing answers nothing: the TV keeps asking, and this request stays hidden.
 */
@Composable
fun RequestDialog(
    request: ConnectionRequest,
    busy: Boolean,
    answer: (allow: Boolean, always: Boolean) -> Unit,
    dismiss: () -> Unit,
) {
    var always by rememberSaveable(request.fingerprint) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) dismiss() },
        icon = { Icon(AccessIcons.Shield, contentDescription = null) },
        title = { Text(Copy.REQUEST_TITLE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Copy.REQUEST_TEXT)
                // Eight pairs a line, so a pair is never split when comparing it with the other device.
                Text(
                    request.fingerprint.split(':').chunked(8).joinToString("\n") { it.joinToString(":") },
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                )
                // No tick-box on the TV's prompt, none here: a tick that can't be applied would mislead.
                if (request.canAlwaysAllow) Row(
                    Modifier.fillMaxWidth().toggleable(always, enabled = !busy, role = Role.Checkbox) { always = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = always, onCheckedChange = null, enabled = !busy)
                    Text(Copy.REQUEST_ALWAYS, Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = { answer(true, always && request.canAlwaysAllow) }, enabled = !busy) { Text(Copy.REQUEST_ALLOW) }
        },
        dismissButton = { TextButton(onClick = { answer(false, false) }, enabled = !busy) { Text(Copy.REQUEST_DENY) } },
    )
}
