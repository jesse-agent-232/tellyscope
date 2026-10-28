// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tellyscope.data.DeviceList
import app.tellyscope.data.Suggested
import app.tellyscope.data.SuggestedGroup
import app.tellyscope.data.Suggestions
import app.tellyscope.ui.DebloatScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DebloatGroupTest {
    @get:Rule val compose = createComposeRule()

    private fun app(pkg: String, ticked: Boolean, optional: Boolean = false) = Suggested(pkg, pkg, null, optional, ticked)

    private val lists = Suggestions.Ready(
        null,
        DeviceList("test-tv", "Test TV", null, null, emptySet(), emptyList()),
        null,
        listOf(
            SuggestedGroup("Extras", listOf(app("a.one", ticked = true), app("a.two", ticked = false, optional = true))),
            SuggestedGroup("Stock launcher", listOf(app("l.home", ticked = false))),
        ),
        emptyList(),
    )

    @Test fun aHeadingTicksItsWholeGroupThenUnticksIt() {
        compose.setContent { DebloatScreen(lists, {}, {}, {}) }
        compose.onNodeWithText("1 of 2").assertExists()
        // Part-ticked: ticks every app, the optional one too.
        compose.onNodeWithText("Extras").performClick()
        compose.onNodeWithText("2 of 2").assertExists()
        compose.onNodeWithText("Disable 2 apps").assertExists()
        // All ticked: unticks them all.
        compose.onNodeWithText("Extras").performClick()
        compose.onNodeWithText("0 of 2").assertExists()
    }

    @Test fun theLauncherGroupHasNoTickAll() {
        compose.setContent { DebloatScreen(lists, {}, {}, {}) }
        compose.onAllNodesWithText("0 of 1").assertCountEquals(0)
        compose.onNodeWithText("Set a new home first, then tick these one at a time.").assertExists()
    }
}
