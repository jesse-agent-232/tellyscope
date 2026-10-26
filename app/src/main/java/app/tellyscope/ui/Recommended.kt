// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import app.tellyscope.data.AppNames

/**
 * A launcher Install a home screen offers, from the bundled `launchers.json`: its name and a line
 * on what it's like.
 */
data class Recommended(val pkg: String, val name: String, val about: String, val accessibility: Boolean)

/** In the picker's order. [Recommended.accessibility] matches `launchers.json`; a test holds them together. */
val RECOMMENDED = listOf(
    Recommended("com.spocky.projengmenu", "Projectivy Launcher", "The most popular. Rows, wallpapers and icons you can change.", true),
    Recommended("com.overdevs.at4k", "AT4K Launcher", "A clean home screen in the Google TV style, without the ads.", true),
    Recommended("com.spauldhaliwal.dispatch", "Dispatch", "Minimal: a fast list of your apps and inputs.", true),
    Recommended("me.efesser.flauncher", "FLauncher", "Simple: your apps in rows, nothing else.", false),
)

fun launcherName(pkg: String) = RECOMMENDED.firstOrNull { it.pkg == pkg }?.name ?: AppNames.fallback(pkg)
