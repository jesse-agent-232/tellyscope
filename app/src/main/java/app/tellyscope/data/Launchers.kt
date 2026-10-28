// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import org.json.JSONObject

/**
 * A third-party launcher Tellyscope knows, from the bundled `lists/launchers.json`. [accessibility]:
 * it keeps Home with its own accessibility service, for TVs whose Home button opens their own
 * launcher whatever the default is, so Make Home turns that service on, and Undo or a switch away
 * from it turns it off. [checked] is the version whose manifest was read. [about] is the line Install
 * a home screen shows; a launcher without one isn't offered there.
 */
data class KnownLauncher(
    val pkg: String,
    val name: String,
    val accessibility: Boolean,
    val checked: String?,
    val about: String? = null,
)

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
            about = l.optStringOrNull("about"),
        ).takeIf { PACKAGE.matches(it.pkg) }
    }
} catch (_: org.json.JSONException) {
    emptyList()
}

/**
 * Package to display name, from the bundled `lists/labels.json`; its groups are only there for
 * people reading the file. Empty unless it is schema 1; names under something that isn't a package
 * are dropped.
 */
fun parseLabels(text: String): Map<String, String> = try {
    val o = JSONObject(text)
    if (o.optInt("schema") != 1) emptyMap() else buildMap {
        o.getJSONArray("groups").objectsOrSkip { it.getJSONObject("labels") }.forEach { labels ->
            for (pkg in labels.keys()) {
                if (pkg != "android" && !PACKAGE.matches(pkg)) continue
                labels.optStringOrNull(pkg)?.let { put(pkg, it) }
            }
        }
    }
} catch (_: org.json.JSONException) {
    emptyMap()
}

/**
 * The lists that ship with the app, from `app/src/main/resources/lists/`. Contributors change them
 * by pull request, and a change reaches people with the next release. Java resources rather than
 * assets, so they load without a Context, in the app and in tests alike.
 */
object BundledLists {
    val launchers: List<KnownLauncher> by lazy { parseLaunchers(read("launchers.json")) }
    val labels: Map<String, String> by lazy { parseLabels(read("labels.json")) }

    private fun read(name: String): String =
        checkNotNull(BundledLists::class.java.getResourceAsStream("/lists/$name")) { "lists/$name isn't bundled" }
            .bufferedReader().use { it.readText() }
}
