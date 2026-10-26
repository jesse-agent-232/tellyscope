// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.AppInfo
import app.tellyscope.data.FoldBook
import app.tellyscope.data.Proc
import app.tellyscope.ui.AppsTab
import app.tellyscope.ui.splitApps
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppsTabsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun app(pkg: String, system: Boolean, locked: String? = null, home: String? = null) =
        AppInfo(pkg, pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }, null, system, true, locked, "1", homeInstead = home)

    private val launcher = app("com.tv.launcher", system = true, locked = "this is your home screen")
    private val apps = listOf(
        app("org.mine.jellyfin", system = false),
        app("org.mine.netflix", system = false),
        app("org.mine.projectivy", system = false, locked = "this is your home screen"),
        app("com.maker.assistant", system = true),
        app("com.maker.ota", system = true),
        app("com.android.systemui", system = true, locked = "the TV needs this to draw the screen"),
        app("com.google.android.gms", system = true, locked = "most apps stop working without it"),
        // A launcher that's protected by name but offered as the new home screen stays changeable.
        app("org.other.home", system = false, locked = "launcher", home = launcher.packageName),
        launcher,
    )
    private fun proc(pkg: String, ram: Int = 50) = Proc(1, pkg, pkg, 1.0, ram)
    private val procs = listOf(proc("org.mine.jellyfin"), proc("com.maker.assistant"), proc("com.android.systemui"), proc("com.maker.ota", ram = 0))

    private fun names(l: List<AppInfo>) = l.map { it.packageName.substringAfterLast('.') }

    @Test fun `running holds the apps using RAM, the user's first, the ones the TV needs under System`() {
        val split = splitApps(apps, procs, AppsTab.Running)
        assertEquals(listOf("jellyfin", "assistant"), names(split.apps))
        assertEquals(listOf("systemui"), names(split.system))
    }

    @Test fun `installed holds only the user's own apps, a locked home screen among them with no System part`() {
        val split = splitApps(apps, procs, AppsTab.Installed)
        assertEquals(listOf("home", "jellyfin", "netflix", "projectivy"), names(split.apps))
        assertEquals(emptyList<String>(), names(split.system))
    }

    @Test fun `running never puts a launcher the user installed under System, even a locked one`() {
        // No default home and the TV's own launcher off: a launcher the user installed stays protected.
        val projectivy = app("com.spocky.projengmenu", system = false, locked = "this is your home screen").copy(removable = true)
        val split = splitApps(listOf(projectivy, launcher), listOf(proc(projectivy.packageName), proc(launcher.packageName)), AppsTab.Running)
        assertEquals(listOf("projengmenu"), names(split.apps))
        assertEquals(listOf("launcher"), names(split.system))
    }

    @Test fun `built-in puts the maker's extras first, then what the TV needs`() {
        val split = splitApps(apps, procs, AppsTab.BuiltIn)
        assertEquals(listOf("assistant", "ota"), names(split.apps))
        assertEquals(listOf("gms", "launcher", "systemui"), names(split.system))
    }

    @Test fun `a fold is kept per tab across restarts, until it is opened again`() {
        val file = tmp.root.resolve("folds.properties")
        FoldBook(file).set(AppsTab.Running.name, true)
        FoldBook(file).set(AppsTab.BuiltIn.name, true)
        assertEquals(setOf("Running", "BuiltIn"), FoldBook(file).folded())
        FoldBook(file).set(AppsTab.Running.name, false)
        assertEquals(setOf("BuiltIn"), FoldBook(file).folded())
    }

    @Test fun `nothing is folded before the user folds it`() {
        assertEquals(emptySet<String>(), FoldBook(tmp.root.resolve("none.properties")).folded())
    }
}
