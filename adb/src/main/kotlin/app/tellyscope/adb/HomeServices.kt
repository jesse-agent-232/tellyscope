// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

/**
 * Launchers that keep Home with their own accessibility service, for TVs whose Home button opens
 * the TV's launcher whatever the default is. Set as default turns that service on; Undo and
 * Switch home back turn it off. An allowlist: an accessibility service can read the screen, so
 * Tellyscope turns one on only for a launcher known to use it for Home.
 */
object HomeServices {
    val LAUNCHERS = setOf(
        "com.spocky.projengmenu", // Projectivy Launcher
        "com.overdevs.at4k", // AT4K Launcher
        "com.spauldhaliwal.dispatch", // Dispatch
    )

    /** [list] is the `enabled_accessibility_services` setting: colon-separated, "null" when unset. */
    fun has(list: String?, service: String) = entries(list).any { full(it) == full(service) }

    fun with(list: String?, service: String) = if (has(list, service)) entries(list) else entries(list) + service

    fun without(list: String?, service: String) = entries(list).filterNot { full(it) == full(service) }

    private fun entries(list: String?) = list.orEmpty().trim().takeUnless { it == "null" }.orEmpty()
        .split(':').filter { it.isNotBlank() }

    /** `pkg/.Service` and `pkg/pkg.Service` name the same service. */
    private fun full(c: String) = c.substringAfter('/').let { cls ->
        c.substringBefore('/') + "/" + if (cls.startsWith('.')) c.substringBefore('/') + cls else cls
    }
}
