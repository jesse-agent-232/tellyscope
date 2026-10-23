// SPDX-License-Identifier: GPL-3.0-only
// Tv and Stick are the Material Icons "tv" and "settings_input_hdmi" (Apache-2.0), see NOTICE.

package app.tellyscope.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import app.tellyscope.data.DeviceKind

/** Generic device shapes in the Material style; no maker's logo or artwork. */
object DeviceIcons {
    private const val TV =
        "M21 3H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h5v2h8v-2h5c1.1 0 1.99-.9 1.99-2L23 5c0-1.1-.9-2-2-2zm0 14H3V5h18v12z"
    private const val STICK =
        "M18 7V4c0-1.1-.9-2-2-2H8c-1.1 0-2 .9-2 2v3H5v6l3 6v3h8v-3l3-6V7h-1zM8 4h8v3h-2V5h-1v2h-2V5h-1v2H8V4z"

    /** A box with a lens on the right and two feet; Material has no projector icon. */
    private const val PROJECTOR =
        "M4 6h16c1.1 0 2 .9 2 2v8c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V8c0-1.1.9-2 2-2z" +
            "M16 8.5a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7z" +
            "M16 10.5a1.5 1.5 0 1 1 0 3a1.5 1.5 0 1 1 0-3z" +
            "M5 9h5v1.5H5z M5 12h5v1.5H5z M4 18h3v1.5H4z M17 18h3v1.5h-3z"

    /** A stick with its plug down and a small remote beside it, for Fire TV; Amazon's logo is theirs. */
    private const val FIRE_TV =
        "M5 2h4c.55 0 1 .45 1 1v12c0 .55-.45 1-1 1H5c-.55 0-1-.45-1-1V3c0-.55.45-1 1-1z" +
            "M5.5 16h3v4h-3z" +
            "M15 6h4c.83 0 1.5.67 1.5 1.5v13c0 .83-.67 1.5-1.5 1.5h-4c-.83 0-1.5-.67-1.5-1.5v-13c0-.83.67-1.5 1.5-1.5z" +
            "M17 8.25a1.75 1.75 0 1 0 0 3.5a1.75 1.75 0 1 0 0-3.5z" +
            "M15.5 14h3v1.5h-3z M15.5 17h3v1.5h-3z"

    val Tv = icon("Tv", TV)
    val FireTv = icon("FireTv", FIRE_TV)
    val Stick = icon("Stick", STICK)
    val Projector = icon("Projector", PROJECTOR)

    fun of(kind: DeviceKind) = when (kind) {
        DeviceKind.Tv -> Tv
        DeviceKind.Projector -> Projector
        DeviceKind.FireTv -> FireTv
        DeviceKind.Stick -> Stick
    }

    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
        .build()
}

/** Screen-reader name for the icon. */
fun DeviceKind.word() = when (this) {
    DeviceKind.Tv -> "TV"
    DeviceKind.Projector -> "Projector"
    DeviceKind.FireTv -> "Fire TV"
    DeviceKind.Stick -> "Streaming device"
}
