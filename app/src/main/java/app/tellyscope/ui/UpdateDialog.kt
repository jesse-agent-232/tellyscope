// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.tellyscope.R
import app.tellyscope.data.Update
import app.tellyscope.data.resolve

/**
 * A newer release is out. Update downloads its APK inside the app and hands it to Android's
 * installer; Get new release opens its GitHub page instead. [updating] is how far Update got.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateDialog(
    update: Update,
    updating: Updating?,
    onUpdate: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    when (updating) {
        null -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.update_title, update.version)) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(boldSpans(dialogNotes(update.notes, stringResource(R.string.update_more_notes)).ifEmpty { stringResource(R.string.update_no_notes) }), style = MaterialTheme.typography.bodyMedium)
                }
            },
            // Three actions stack full width; without an APK in the release, only its page is left.
            confirmButton = {
                Column(Modifier.fillMaxWidth()) {
                    if (update.apk != null) {
                        Button(onClick = onUpdate, Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_install)) }
                        TextButton(onClick = onRelease, Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_release)) }
                    } else {
                        Button(onClick = onRelease, Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_release)) }
                    }
                    TextButton(onClick = onCancel, Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_cancel)) }
                }
            },
        )
        Updating.Allow -> AlertDialog(
            onDismissRequest = onBack,
            icon = { Icon(SystemUpdate, contentDescription = null) },
            title = { Text(stringResource(R.string.update_allow_title)) },
            text = { Text(boldSpans(stringResource(R.string.update_allow_text)), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { Button(onClick = onOpenSettings) { Text(stringResource(R.string.update_open_settings)) } },
            dismissButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.update_cancel)) } },
        )
        is Updating.Downloading -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.update_downloading, update.version)) },
            text = {
                val p = updating.progress
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val fraction = p.fraction
                    if (fraction != null) LinearWavyProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth())
                    else LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.update_of, Copy.bytes(p.done).resolve(), Copy.bytes(p.total ?: update.size).resolve()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.update_cancel)) } },
        )
        Updating.Failed -> AlertDialog(
            onDismissRequest = onCancel,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.update_failed_title, update.version)) },
            text = { Text(stringResource(R.string.update_failed_text), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { Button(onClick = onUpdate) { Text(stringResource(R.string.update_try_again)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.update_cancel)) }
                    TextButton(onClick = onRelease) { Text(stringResource(R.string.update_release)) }
                }
            },
        )
    }
}

/**
 * release.yml's body as plain lines: the CHANGELOG part (prod) or the alpha line, without the
 * commit list, code ticks or the trailing PR links, capped at [max] lines, then [more]. `**` stays for [boldSpans].
 */
internal fun dialogNotes(body: String, more: String, max: Int = 12): String {
    val lines = body.substringBefore("### Commits").lines()
        .map { it.replace("`", "").replace(PR_LINK, "").trimEnd() }
        .filter { it.isNotBlank() }
    return (if (lines.size > max) lines.take(max) + more else lines).joinToString("\n")
}

private val PR_LINK = Regex("""\s*\[#\d+]\([^)]*\)\s*$""")

/** Each `**…**` pair becomes bold, stars removed; an unmatched `**` is dropped. */
internal fun boldSpans(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, part ->
        // Odd parts sit inside a pair, unless the last one, whose opening `**` has no partner.
        if (i % 2 == 1 && i < parts.lastIndex) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}
