// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tellyscope.data.AppInfo

/**
 * The separate yes for a launcher's accessibility service, which can see the screen, so it is
 * never turned on as a side effect of a switch. [other] is the launcher Home may open without it;
 * [dismissLabel] is "Switch without it" during a switch, "Not now" after one.
 */
@Composable
fun ServiceDialog(app: String, other: String, onTurnOn: () -> Unit, dismissLabel: String, onOther: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(DeviceIcons.Accessibility, contentDescription = null) },
        title = { Text(Copy.serviceTitle(app)) },
        text = { Text(Copy.explainService(app, other)) },
        confirmButton = { TextButton(onClick = onTurnOn) { Text(Copy.TURN_IT_ON) } },
        dismissButton = { TextButton(onClick = onOther) { Text(dismissLabel) } },
    )
}

/**
 * On the TV page after a switch left [app]'s service off: Home may still open the TV's own
 * launcher. Got it puts it away until the next switch; Turn on asks the same separate yes.
 */
@Composable
fun ServiceOffCard(app: AppInfo, onTurnOn: () -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var asking by rememberSaveable { mutableStateOf(false) }
    if (asking) {
        ServiceDialog(
            app.label, app.homeBack ?: Copy.TV_OWN_LAUNCHER,
            onTurnOn = { asking = false; onTurnOn() },
            dismissLabel = Copy.NOT_NOW, onOther = { asking = false }, onDismiss = { asking = false },
        )
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = c.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().testTag("serviceOff"),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
            Row {
                Box(Modifier.size(40.dp).background(c.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = c.onTertiaryContainer, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(Copy.serviceOffTitle(app.homeBack), style = MaterialTheme.typography.titleMedium)
                    Text(
                        Copy.serviceOffText(app.label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(Copy.GOT_IT) }
                FilledTonalButton(onClick = { asking = true }) { Text(Copy.TURN_ON) }
            }
        }
    }
}
