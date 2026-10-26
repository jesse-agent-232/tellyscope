// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import app.tellyscope.data.AppNames

/**
 * A launcher Install a home screen offers, from the bundled `launchers.json`: its name, a line on
 * what it's like, and the letter tile's colour (there's no icon to show before it's installed).
 */
data class Recommended(val pkg: String, val name: String, val about: String, val accessibility: Boolean, val tile: Long)

/** In the picker's order. [Recommended.accessibility] matches `launchers.json`; a test holds them together. */
val RECOMMENDED = listOf(
    Recommended("com.spocky.projengmenu", "Projectivy Launcher", "The most popular. Rows, wallpapers and icons you can change.", true, 0xFF3D5AFE),
    Recommended("com.overdevs.at4k", "AT4K Launcher", "A clean home screen in the Google TV style, without the ads.", true, 0xFF6A1B9A),
    Recommended("com.spauldhaliwal.dispatch", "Dispatch", "Minimal: a fast list of your apps and inputs.", true, 0xFFC62828),
    Recommended("me.efesser.flauncher", "FLauncher", "Simple: your apps in rows, nothing else.", false, 0xFF2E7D32),
)

fun launcherName(pkg: String) = RECOMMENDED.firstOrNull { it.pkg == pkg }?.name ?: AppNames.fallback(pkg)
