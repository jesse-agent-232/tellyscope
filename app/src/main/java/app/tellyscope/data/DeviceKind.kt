// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

/** What sort of box a TV is, for its icon. A guess from what the TV and the network report. */
enum class DeviceKind { Tv, Projector, Stick }

/** Makers whose Android devices are all projectors. */
private val PROJECTOR_MAKERS = setOf("xgimi", "nebula", "dangbei", "formovie")

/** Streaming sticks and boxes, by model name. Fire TV models all start with AFT (AFTMM, AFTKA). */
private val STICK = Regex("^aft|chromecast|stick|streamer|shield", RegexOption.IGNORE_CASE)

/** Projector makers or "projector" in a name, then sticks and boxes; anything else is a TV. */
fun deviceKind(d: Device): DeviceKind {
    val words = listOfNotNull(d.manufacturer, d.hit.maker).flatMap { it.lowercase().split(Regex("[^a-z]+")) }
    val names = listOfNotNull(d.model, d.hit.hostname)
    return when {
        words.any { it in PROJECTOR_MAKERS } || names.any { "projector" in it.lowercase() } -> DeviceKind.Projector
        names.any { STICK.containsMatchIn(it) } -> DeviceKind.Stick
        else -> DeviceKind.Tv
    }
}
