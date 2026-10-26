// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.Action
import app.tellyscope.data.HistoryEntry
import app.tellyscope.data.uninstallAfterHomeBack
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uninstalling the launcher on Home: Home goes back first, and nothing is removed if that fails. */
class UninstallHomeTest {
    private val steps = mutableListOf<String>()
    private val removed = HistoryEntry(1, "tv", 0L, Action.Uninstall, "com.spocky.projengmenu", "Projectivy Launcher", null, null)

    private suspend fun uninstall(): HistoryEntry {
        steps += "uninstall"
        return removed
    }

    @Test fun homeGoesBackBeforeTheUninstall() = runTest {
        assertEquals(removed, uninstallAfterHomeBack({ steps += "home back" }, ::uninstall))
        assertEquals(listOf("home back", "uninstall"), steps)
    }

    @Test fun aSwitchThatFailsUninstallsNothingAndSaysSo() = runTest {
        val e = runCatching { uninstallAfterHomeBack({ throw IllegalStateException("the TV kept the home screen it has") }, ::uninstall) }.exceptionOrNull()
        assertEquals("Home didn't switch back: the TV kept the home screen it has. Nothing was uninstalled", e?.message)
        assertTrue(steps.isEmpty())
    }

    @Test fun aServiceStillOnUninstallsNothing() = runTest {
        val why = "switched back, but Projectivy Launcher's accessibility service is still on. Turn it off in the TV's Settings → Accessibility"
        val e = runCatching { uninstallAfterHomeBack({ throw IllegalStateException(why) }, ::uninstall) }.exceptionOrNull()
        assertEquals("$why. Nothing was uninstalled", e?.message)
        assertTrue(steps.isEmpty())
    }
}
