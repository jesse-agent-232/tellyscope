// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

/** What sort of box a TV is, for its icon. A guess from what the TV and the network report. */
enum class DeviceKind { Tv, Projector, FireTv, Stick }

/** Makers whose Android devices are all projectors. */
private val PROJECTOR_MAKERS = setOf("xgimi", "nebula", "dangbei", "formovie")

/** Fire TV models all start with AFT (AFTMM, AFTKA); a network name may just say so. */
private val FIRE_TV = Regex("^aft|fire[ -]?tv", RegexOption.IGNORE_CASE)

/** Other streaming sticks and boxes, by model or network name. */
private val STICK = Regex("chromecast|stick|streamer|shield", RegexOption.IGNORE_CASE)

/** Projector makers or "projector" in a name, then Amazon, then other sticks and boxes; anything else is a TV. */
fun deviceKind(d: Device): DeviceKind {
    val words = listOfNotNull(d.manufacturer, d.hit.maker).flatMap { it.lowercase().split(Regex("[^a-z]+")) }
    val names = listOfNotNull(d.model, d.hit.hostname)
    return when {
        words.any { it in PROJECTOR_MAKERS } || names.any { "projector" in it.lowercase() } -> DeviceKind.Projector
        "amazon" in words || names.any { FIRE_TV.containsMatchIn(it) } -> DeviceKind.FireTv
        names.any { STICK.containsMatchIn(it) } -> DeviceKind.Stick
        else -> DeviceKind.Tv
    }
}
