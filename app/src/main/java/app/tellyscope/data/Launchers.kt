// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import org.json.JSONObject

/**
 * A third-party launcher Tellyscope knows, from `assets/launchers.json`. [accessibility]: it keeps
 * Home with its own accessibility service, for TVs whose Home button opens their own launcher
 * whatever the default is, so Set as default turns that service on and Undo / Switch home back
 * turn it off. [checked] is the version whose manifest was read.
 */
data class KnownLauncher(val pkg: String, val name: String, val accessibility: Boolean, val checked: String?)

/**
 * Bundled with the app, never fetched: an accessibility service can read the screen, so only a
 * list that went through review may turn one on. Empty unless it is schema 1; entries that can't
 * be read or aren't package names are dropped.
 */
fun parseLaunchers(text: String): List<KnownLauncher> = try {
    val o = JSONObject(text)
    if (o.optInt("schema") != 1) emptyList() else o.getJSONArray("launchers").objectsOrSkip { l ->
        KnownLauncher(
            pkg = l.getString("package"),
            name = l.getString("name"),
            accessibility = l.optBoolean("accessibility"),
            checked = l.optStringOrNull("checked"),
        ).takeIf { PACKAGE.matches(it.pkg) }
    }
} catch (_: org.json.JSONException) {
    emptyList()
}
