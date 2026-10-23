// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import app.tellyscope.adb.Tweak

/** What each TV tweak writes, and how History names it afterwards. */
object TvSettings {
    /** AdGuard's public ad-blocking resolver: what the ad-blocking switch offers first. */
    const val ADGUARD = "dns.adguard-dns.com"
    const val FAST = "0.5"
    /** History's name for a cache clear: "Cleared app caches". */
    const val CACHES = "app caches"

    /** Off goes back to Automatic, Android's default, rather than turning encrypted DNS off. */
    fun dns(host: String?): Map<String, String?> =
        if (host == null) mapOf("private_dns_mode" to "opportunistic")
        else mapOf("private_dns_mode" to "hostname", "private_dns_specifier" to host)

    fun animations(fast: Boolean): Map<String, String?> = Tweak.ANIMATIONS.keys.associateWith { if (fast) FAST else "1.0" }

    fun tweaks(dns: Map<String, String?>, animations: Map<String, String?>) = TvTweaks(
        dnsHost = dns["private_dns_specifier"]?.takeIf { dns["private_dns_mode"] == "hostname" },
        animationScale = animations["window_animation_scale"],
    )

    /** History's line for a change that left the TV at [values]. */
    fun label(tweak: Tweak, values: Map<String, String?>): String = when (tweak) {
        Tweak.PRIVATE_DNS -> when (values["private_dns_mode"]) {
            "hostname" -> "Private DNS set to ${values["private_dns_specifier"]}"
            "off" -> "Private DNS turned off"
            else -> "Private DNS set to Automatic"
        }
        Tweak.ANIMATIONS -> when (val v = values["window_animation_scale"]?.toFloatOrNull()) {
            null, 1f -> "Animations at normal speed"
            0f -> "Animations turned off"
            else -> "Animations at ${v.toString().removeSuffix(".0")}×"
        }
    }
}
