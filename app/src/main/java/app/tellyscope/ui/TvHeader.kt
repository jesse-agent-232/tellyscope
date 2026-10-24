// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tellyscope.data.Device
import app.tellyscope.data.DeviceState
import app.tellyscope.data.Snapshot
import app.tellyscope.data.deviceKind

/*
 * The TV's header, shared by the tabs that lead with the TV: the big hero, which collapses as the
 * page scrolls, and the bar title it collapses into. The TV chips, on Overview, scroll away with the page.
 */

/**
 * How far the hero has scrolled under the bar, 0 (all in view) to 1 (gone), from the list's
 * first visible item and its offset. The hero is item 0, [heroHeight] px tall; 0 before it's measured.
 */
internal fun collapsedFraction(firstIndex: Int, firstOffset: Int, heroHeight: Int): Float = when {
    firstIndex > 0 -> 1f
    heroHeight <= 0 -> 0f
    else -> (firstOffset.toFloat() / heroHeight).coerceIn(0f, 1f)
}

/** The maker line and health pill go first, over the first half of the collapse. */
internal fun detailAlpha(f: Float) = (1f - 2f * f).coerceIn(0f, 1f)

/** The bar title comes in over the last third, as the hero's name passes under the bar. */
internal fun titleAlpha(f: Float) = ((f - 0.67f) / 0.33f).coerceIn(0f, 1f)

/** The TV's cookie tile: primary while it's connected, grey when it isn't. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Cookie(device: Device, size: Dp) {
    val kind = deviceKind(device)
    val c = MaterialTheme.colorScheme
    val on = device.state == DeviceState.Online
    Box(
        Modifier.size(size).clip(MaterialShapes.Cookie9Sided.toShape()).background(if (on) c.primaryContainer else c.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(DeviceIcons.of(kind), contentDescription = kind.word(), Modifier.size(size / 2), tint = if (on) c.onPrimaryContainer else c.outline)
    }
}

/**
 * The TV as the hero: an Expressive cookie-shaped tile, its name, how it's doing, and, on
 * Overview ([chips]), the TV chips. [collapse] is read while drawing only, so scrolling redraws
 * without recomposing: the cookie and name shrink and fade, the details faster. [onHeight] gets
 * the height of everything above the chips.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TvHero(state: UiState, live: Snapshot?, actions: DeviceActions, chips: Boolean = true, collapse: () -> Float = { 0f }, onHeight: (Int) -> Unit = {}) {
    val device = state.selected ?: return
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // No gap between the two parts: the maker line sits right under the name, as before the collapse.
        Column(Modifier.fillMaxWidth().onSizeChanged { onHeight(it.height) }, horizontalAlignment = Alignment.CenterHorizontally) {
            // Toward the top left, where the bar's small cookie and name take over.
            Column(
                Modifier.graphicsLayer {
                    val f = collapse()
                    scaleX = 1f - 0.4f * f
                    scaleY = scaleX
                    alpha = 1f - f
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Cookie(device, 80.dp)
                Text(name(device), style = MaterialTheme.typography.headlineMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Column(Modifier.graphicsLayer { alpha = detailAlpha(collapse()) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Under a model-name title, the model would only say it twice.
                val maker = device.manufacturer ?: device.hit.maker
                val made = if (name(device) == device.model) maker.orEmpty() else made(maker, device.model)
                if (made.isNotEmpty()) Text(made, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
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
            }
        }
        if (chips) TvChips(state, actions)
    }
}

/** The collapsed hero, in the bar: small cookie, the TV's name, and whether it's connected. */
@Composable
fun TvBarTitle(device: Device, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val on = device.state == DeviceState.Online
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Cookie(device, 32.dp)
        Spacer(Modifier.width(12.dp))
        Text(name(device), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(12.dp))
        Surface(shape = CircleShape, color = if (on) c.primaryContainer else c.surfaceContainerHighest, contentColor = if (on) c.onPrimaryContainer else c.onSurfaceVariant) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                StateDot(device.state)
                Spacer(Modifier.width(6.dp))
                Text(Copy.state(device.state), style = MaterialTheme.typography.labelMedium)
            }
        }
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
