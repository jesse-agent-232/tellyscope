// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.tellyscope.adb.RemoteKey
import app.tellyscope.data.DeviceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

data class RemoteActions(
    /** Suspends until the TV answers, so a held key never queues up presses. False when it didn't take the key. */
    val press: suspend (RemoteKey) -> Boolean = { true },
    /** Returns why the text can't be typed, or null once it's sent. */
    val type: (String) -> String? = { null },
    val reboot: () -> Unit = {},
    val back: () -> Unit = {},
)

/** A held key repeats after this, then as fast as the TV takes them. */
private const val REPEAT_AFTER_MS = 400L

/**
 * One press, then repeats while [held] for keys that repeat. Stops at the first key the TV didn't
 * take (refused, dropped, or no TV selected), and yields between repeats, so a press that answers
 * at once can't keep the main thread busy.
 */
internal suspend fun repeatWhileHeld(key: RemoteKey, held: () -> Boolean, press: suspend (RemoteKey) -> Boolean) {
    if (!press(key) || !key.repeats) return
    delay(REPEAT_AFTER_MS)
    while (held() && press(key)) yield()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    state: UiState,
    actions: RemoteActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val device = state.selected ?: return
    val restart = state.restarting[device.id]
    val live = device.state == DeviceState.Online && restart == null
    var confirmReboot by rememberSaveable { mutableStateOf(false) }
    if (confirmReboot) RebootDialog(onRestart = { confirmReboot = false; actions.reboot() }, onDismiss = { confirmReboot = false })

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Remote") },
                navigationIcon = {
                    IconButton(onClick = actions.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the TV's page") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when {
                restart != null -> RestartingCard(restart, state.now)
                device.state != DeviceState.Online -> Text(
                    Copy.REMOTE_OFFLINE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                Modifier.alpha(if (live) 1f else 0.38f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                DPad(live, actions.press)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundKey(RemoteKey.BACK, "Back", live, actions.press) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    RoundKey(RemoteKey.HOME, "Home", live, actions.press) { Icon(Icons.Default.Home, null) }
                    RoundKey(RemoteKey.MENU, "Menu", live, actions.press) { Icon(Icons.Default.Menu, null) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillKey(RemoteKey.VOLUME_DOWN, "Volume down", live, actions.press, Modifier.weight(1f)) { Text("Vol −") }
                    PillKey(RemoteKey.MUTE, "Mute", live, actions.press, Modifier.weight(1f)) { Text("Mute") }
                    PillKey(RemoteKey.VOLUME_UP, "Volume up", live, actions.press, Modifier.weight(1f)) { Text("Vol +") }
                }
                PillKey(RemoteKey.PLAY_PAUSE, "Play or pause", live, actions.press, Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play / Pause")
                    }
                }
                Keyboard(live, actions.type)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val scope = rememberCoroutineScope()
                    OutlinedButton(onClick = { scope.launch { actions.press(RemoteKey.SLEEP) } }, enabled = live, modifier = Modifier.weight(1f)) {
                        Text("Sleep")
                    }
                    OutlinedButton(onClick = { confirmReboot = true }, enabled = live, modifier = Modifier.weight(1f)) {
                        Text("Restart TV")
                    }
                }
            }
        }
    }
}

/** The four arrows around a big OK, shaped like the remote in the user's other hand. */
@Composable
private fun DPad(enabled: Boolean, press: suspend (RemoteKey) -> Boolean) {
    Surface(Modifier.size(272.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box {
            val arrow = Modifier.size(80.dp)
            val tint = MaterialTheme.colorScheme.onSurface
            KeySurface(RemoteKey.UP, "Up", enabled, press, CircleShape, Color.Transparent, arrow.align(Alignment.TopCenter)) {
                Icon(Icons.Default.KeyboardArrowUp, null, Modifier.size(40.dp), tint = tint)
            }
            KeySurface(RemoteKey.DOWN, "Down", enabled, press, CircleShape, Color.Transparent, arrow.align(Alignment.BottomCenter)) {
                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(40.dp), tint = tint)
            }
            KeySurface(RemoteKey.LEFT, "Left", enabled, press, CircleShape, Color.Transparent, arrow.align(Alignment.CenterStart)) {
                Icon(Icons.Default.KeyboardArrowLeft, null, Modifier.size(40.dp), tint = tint)
            }
            KeySurface(RemoteKey.RIGHT, "Right", enabled, press, CircleShape, Color.Transparent, arrow.align(Alignment.CenterEnd)) {
                Icon(Icons.Default.KeyboardArrowRight, null, Modifier.size(40.dp), tint = tint)
            }
            KeySurface(
                RemoteKey.OK, "OK", enabled, press, CircleShape, MaterialTheme.colorScheme.primary,
                Modifier.size(112.dp).align(Alignment.Center),
            ) {
                Text("OK", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun RoundKey(key: RemoteKey, label: String, enabled: Boolean, press: suspend (RemoteKey) -> Boolean, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KeySurface(key, label, enabled, press, CircleShape, MaterialTheme.colorScheme.secondaryContainer, Modifier.size(64.dp), icon)
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PillKey(
    key: RemoteKey,
    label: String,
    enabled: Boolean,
    press: suspend (RemoteKey) -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = KeySurface(key, label, enabled, press, MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.secondaryContainer, modifier.height(56.dp), content)

/**
 * One remote key. Not a Button: a held [RemoteKey.repeats] key has to keep sending until the
 * finger lifts, and each repeat waits for the TV to take the last one so nothing piles up.
 */
@Composable
private fun KeySurface(
    key: RemoteKey,
    label: String,
    enabled: Boolean,
    press: suspend (RemoteKey) -> Boolean,
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val interactions = remember { MutableInteractionSource() }
    Surface(
        shape = shape,
        color = color,
        modifier = modifier
            .clip(shape)
            .indication(interactions, ripple())
            .semantics {
                role = Role.Button
                contentDescription = label
                if (enabled) onClick { scope.launch { press(key) }; true }
            }
            .pointerInput(key, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val p = PressInteraction.Press(down.position)
                    interactions.tryEmit(p)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    var held = true
                    scope.launch { repeatWhileHeld(key, { held }, press) }
                    waitForUpOrCancellation()
                    held = false
                    interactions.tryEmit(PressInteraction.Release(p))
                }
            },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun Keyboard(enabled: Boolean, type: (String) -> String?) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    fun send() {
        error = type(text)
        if (error == null) text = ""
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; error = null },
        enabled = enabled,
        label = { Text("Type on the TV") },
        supportingText = { Text(error ?: Copy.TYPE_HINT) },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { send() }),
        trailingIcon = {
            IconButton(onClick = ::send, enabled = enabled && text.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send to the TV")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun RebootDialog(onRestart: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Copy.REBOOT_TITLE) },
        text = { Text(Copy.REBOOT_TEXT) },
        confirmButton = { Button(onClick = onRestart) { Text("Restart") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shown instead of the offline card while a TV restarts: it's expected, not an error. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RestartingCard(restart: Restart, now: Long) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Restarting", style = MaterialTheme.typography.titleSmall)
                Text(Copy.restarting((now - restart.since) / 60_000), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
