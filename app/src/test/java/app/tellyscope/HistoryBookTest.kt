// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.Action
import app.tellyscope.data.FakeDeviceSource
import app.tellyscope.data.HistoryBook
import app.tellyscope.data.HistoryEntry
import app.tellyscope.data.historyKey
import app.tellyscope.data.restoreEach
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryBookTest {
    @get:Rule val tmp = TemporaryFolder()

    private val tv = "tv-1"

    @Test fun aSettingsPreviousValuesSurviveARestart() {
        val file = tmp.root.resolve("history.tsv")
        val previous = mapOf("private_dns_mode" to "opportunistic", "private_dns_specifier" to null, "odd" to "a=b,c")
        val change = HistoryBook(file).record(tv, 1_000, Action.Change, "PRIVATE_DNS", "Private DNS set to x", Action.Change, previous = previous)
        assertEquals(listOf(change), HistoryBook(file).entries(tv))
    }

    @Test fun historyAndUndoSurviveARestart() {
        val file = tmp.root.resolve("history.tsv")
        val before = HistoryBook(file)
        before.setBaseline(tv, setOf("com.old.off"))
        val disabled = before.record(tv, 1_000, Action.Disable, "com.netflix.ninja", "Netflix", undo = Action.Enable)

        // Next launch: same file, new book. The entry is there and can still be undone.
        val after = HistoryBook(file)
        assertEquals(listOf(disabled), after.entries(tv))
        assertTrue(after.hasBaseline(tv))
        after.record(tv, 2_000, Action.Enable, "com.netflix.ninja", "Netflix", undo = Action.Disable, undoing = disabled.id)

        val again = HistoryBook(file).entries(tv)
        assertEquals(2, again.size)
        assertEquals(2_000L, again.single { it.id == disabled.id }.undoneAt)
        assertNull(again.first().undoneAt)
        assertTrue(again.first().id > disabled.id)
    }

    @Test fun baselineIsWrittenOnlyOnce() {
        val book = HistoryBook(tmp.root.resolve("history.tsv"))
        book.setBaseline(tv, setOf("com.old.off"))
        book.setBaseline(tv, emptySet())
        book.record(tv, 1, Action.Disable, "com.old.off", "Old", Action.Enable)
        assertEquals(emptyList<String>(), book.restorable(tv))
    }

    @Test fun restoreSkipsWhatWasOffBeforeAndWhatWasTurnedBackOn() {
        val book = HistoryBook()
        book.setBaseline(tv, setOf("com.old.off"))
        book.record(tv, 1, Action.Disable, "com.netflix.ninja", "Netflix", Action.Enable)
        book.record(tv, 2, Action.Disable, "org.jellyfin.androidtv", "Jellyfin", Action.Enable)
        book.record(tv, 3, Action.Enable, "org.jellyfin.androidtv", "Jellyfin", Action.Disable)
        book.record(tv, 4, Action.Disable, "com.old.off", "Old", Action.Enable)
        book.record(tv, 5, Action.Stop, "com.google.android.youtube.tv", "YouTube", Action.Open)
        book.record("other-tv", 6, Action.Disable, "com.xgimi.ota", "XGIMI Updates", Action.Enable)
        assertEquals(listOf("com.netflix.ninja"), book.restorable(tv))
    }

    @Test fun damagedLinesCostOnlyThemselves() {
        val file = tmp.root.resolve("history.tsv")
        HistoryBook(file).record(tv, 1, Action.Disable, "com.netflix.ninja", "Net\tflix", Action.Enable)
        file.appendText("v1\te\ttv-1\tnot-a-number\n")
        file.appendText("v2\te\ttv-1\t9\t9\tDisable\tx\tx\t\t\n")
        val entries = HistoryBook(file).entries(tv)
        assertEquals(1, entries.size)
        assertEquals("Net flix", entries.single().label)
    }

    @Test fun uninstallHasNoUndoAndRestoreForgetsTheApp() = runTest {
        val source = FakeDeviceSource()
        val id = "tv"
        source.act(id, Action.Disable, "com.netflix.ninja")
        val gone = source.act(id, Action.Uninstall, "com.netflix.ninja")

        assertNull(gone.undo)
        assertTrue(source.apps(id).none { it.packageName == "com.netflix.ninja" })
        assertEquals(emptyList<String>(), HistoryBook.leftDisabled(source.history(id)))
        assertEquals(emptyList<String>(), source.restoreAll(id).map { it.packageName })
    }

    @Test fun restoreAllTurnsOnOnlyWhatTellyscopeDisabled() = runTest {
        // The fake TV starts with TV Recommendations already disabled.
        val source = FakeDeviceSource()
        val id = "tv"
        source.act(id, Action.Disable, "com.netflix.ninja")
        source.act(id, Action.Disable, "org.jellyfin.androidtv")
        source.act(id, Action.Enable, "com.google.android.tvrecommendations")
        source.act(id, Action.Disable, "com.google.android.tvrecommendations")

        val restored = source.restoreAll(id)

        assertEquals(setOf("com.netflix.ninja", "org.jellyfin.androidtv"), restored.map { it.packageName }.toSet())
        val apps = source.apps(id).associateBy { it.packageName }
        assertTrue(apps.getValue("com.netflix.ninja").enabled)
        assertTrue(apps.getValue("org.jellyfin.androidtv").enabled)
        assertFalse(apps.getValue("com.google.android.tvrecommendations").enabled)
        assertEquals(emptyList<String>(), source.restoreAll(id).map { it.packageName })
    }

    @Test fun historyFollowsTheSerialNotTheAddress() {
        assertEquals("sn:XK03H1234", historyKey(" XK03H1234\n", "a1b2", "tv-1"))
        // Placeholder serials some boxes ship would mix up every TV of that model.
        assertEquals("id:a1b2c3", historyKey("0123456789ABCDEF", "a1b2c3", "tv-1"))
        assertEquals("id:a1b2c3", historyKey("unknown", "a1b2c3\n", "tv-1"))
        assertEquals("tv-1", historyKey("", "0000000000000000", "tv-1"))
        assertEquals("tv-1", historyKey(null, null, "tv-1"))
    }

    @Test fun historyRecordedBeforeTheKeyMovesOverToIt() {
        val file = tmp.root.resolve("history.tsv")
        val book = HistoryBook(file)
        book.setBaseline(tv, setOf("com.old.off"))
        book.record(tv, 1, Action.Disable, "com.netflix.ninja", "Netflix", Action.Enable)
        book.rekey(tv, "sn:XK03H1234")

        val after = HistoryBook(file)
        assertEquals(emptyList<Any>(), after.entries(tv))
        assertEquals(listOf("com.netflix.ninja"), after.entries("sn:XK03H1234").map { it.packageName })
        assertEquals(listOf("com.netflix.ninja"), after.restorable("sn:XK03H1234"))
        assertFalse(after.hasBaseline(tv))
    }

    @Test fun restoreMarksTheDisablesItReversed() = runTest {
        val source = FakeDeviceSource()
        val id = source.known().first().id
        source.act(id, Action.Disable, "com.netflix.ninja")
        source.restoreAll(id)
        val disable = source.history(id).single { it.action == Action.Disable }
        // Already back on: no Undo left to offer.
        assertTrue(disable.undoneAt != null)
    }

    @Test fun aStoppedRestoreSaysHowFarItGot() = runTest {
        var calls = 0
        val e = runCatching {
            restoreEach(listOf("a", "b", "c")) { pkg ->
                if (++calls == 3) throw IllegalStateException("the TV refused")
                HistoryEntry(calls.toLong(), tv, 0, Action.Enable, pkg, pkg, Action.Disable, null)
            }
        }.exceptionOrNull()
        assertEquals("turned 2 apps back on, then stopped: the TV refused", e?.message)
    }

    @Test fun aDroppedConnectionStillSaysHowFarItGot() = runTest {
        val e = runCatching {
            restoreEach(listOf("a", "b")) { pkg ->
                if (pkg == "b") throw java.io.IOException("connection reset")
                HistoryEntry(1, tv, 0, Action.Enable, pkg, pkg, Action.Disable, null)
            }
        }.exceptionOrNull()
        assertEquals("turned 1 app back on, then stopped: connection reset", e?.message)
    }
}
