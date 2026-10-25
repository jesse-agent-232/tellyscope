// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.KnownLauncher
import app.tellyscope.data.parseLaunchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** org.json is Android's, so this runs under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchersTest {
    @Test fun theBundledListReadsWhole() {
        val text = File("src/main/assets/launchers.json").readText()
        val launchers = parseLaunchers(text)
        assertEquals(Regex("\"package\"").findAll(text).count(), launchers.size) // nothing dropped
        assertEquals(launchers.size, launchers.map { it.pkg }.toSet().size)
        assertEquals(
            setOf("com.spocky.projengmenu", "com.overdevs.at4k", "com.spauldhaliwal.dispatch"),
            launchers.filter { it.accessibility }.map { it.pkg }.toSet(),
        )
    }

    @Test fun badEntriesAreDroppedAndOnlySchemaOneCounts() {
        val text = """{"schema":1,"launchers":[
            {"package":"com.good.launcher","name":"Good","accessibility":true},
            {"package":"not a package; rm -rf /","name":"Bad","accessibility":true},
            {"name":"No package"},
            "not an object"
        ]}"""
        assertEquals(listOf(KnownLauncher("com.good.launcher", "Good", true, null)), parseLaunchers(text))
        assertTrue(parseLaunchers(text.replace("\"schema\":1", "\"schema\":2")).isEmpty())
        assertTrue(parseLaunchers("{").isEmpty())
    }
}
