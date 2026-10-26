// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tellyscope.data.Action
import app.tellyscope.data.AppInfo
import app.tellyscope.ui.AppRow
import app.tellyscope.ui.Copy
import app.tellyscope.ui.DeviceActions
import app.tellyscope.ui.ServiceOffCard
import app.tellyscope.ui.UninstallDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A launcher's accessibility service is its own yes, never a side effect of the switch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeServiceTest {
    @get:Rule val compose = createComposeRule()

    private val pkg = "com.spocky.projengmenu"
    private val projectivy = AppInfo(pkg, "Projectivy Launcher", null, false, true, null, "1.0", homeInstead = "com.google.android.tvlauncher", homeService = true)

    @Test fun theSwitchAsksAboutTheServiceSeparately() {
        val answers = mutableListOf<Boolean>()
        compose.setContent {
            AppRow(projectivy, emptyList(), pkg to Action.Home, busy = false, actions = DeviceActions(confirmHome = { answers += it }), homeLabel = "Google TV Home")
        }
        compose.onNodeWithText(Copy.confirm(Action.Home)).performClick()
        compose.onNodeWithText(Copy.serviceTitle("Projectivy Launcher")).assertExists()
        compose.onNodeWithText(Copy.SWITCH_WITHOUT).performClick()
        assertEquals(listOf(false), answers)
    }

    @Test fun turnItOnSwitchesWithTheService() {
        val answers = mutableListOf<Boolean>()
        compose.setContent {
            AppRow(projectivy, emptyList(), pkg to Action.Home, busy = false, actions = DeviceActions(confirmHome = { answers += it }), homeLabel = "Google TV Home")
        }
        compose.onNodeWithText(Copy.confirm(Action.Home)).performClick()
        compose.onNodeWithText(Copy.TURN_IT_ON).performClick()
        assertEquals(listOf(true), answers)
    }

    @Test fun aLauncherWithoutAServiceSwitchesStraightAway() {
        var confirmed = false
        compose.setContent {
            AppRow(projectivy.copy(homeService = false), emptyList(), pkg to Action.Home, busy = false, actions = DeviceActions(confirm = { confirmed = true }), homeLabel = "Google TV Home")
        }
        compose.onNodeWithText(Copy.confirm(Action.Home)).performClick()
        compose.onNodeWithText(Copy.serviceTitle("Projectivy Launcher")).assertDoesNotExist()
        assertTrue(confirmed)
    }

    @Test fun withNoDefaultHomeTheUsersLauncherOffersSetAsDefaultAndAPlainUninstall() {
        var proposed: Pair<String, Action>? = null
        compose.setContent {
            AppRow(projectivy.copy(removable = true), emptyList(), null, busy = false, actions = DeviceActions(propose = { p, a -> proposed = p to a }), homeLabel = "Google TV Home")
        }
        compose.onNodeWithText("Projectivy Launcher").performClick()
        compose.onNodeWithText(Copy.MAKE_HOME).assertExists()
        compose.onNodeWithText(Copy.button(Action.Uninstall)).performClick()
        assertEquals(pkg to Action.Uninstall, proposed)
    }

    @Test fun withNoLauncherOfTheTvsOwnOnSetAsDefaultSaysNothingIsTurnedOff() {
        var confirmed = false
        val first = projectivy.copy(homeInstead = null, homeFirst = true, homeService = false, removable = true)
        compose.setContent { AppRow(first, emptyList(), pkg to Action.Home, busy = false, actions = DeviceActions(confirm = { confirmed = true })) }
        compose.onNodeWithText(Copy.explainFirstHome("Projectivy Launcher", pkg)).assertExists()
        compose.onNodeWithText(Copy.confirm(Action.Home)).performClick()
        assertTrue(confirmed)
    }

    private val onHome = projectivy.copy(homeInstead = null, homeBack = "Google TV Home", homeServiceOff = true, homeServiceNote = true, protectedReason = "this is your home screen")

    @Test fun theLauncherOnHomeOffersUninstallAndItsConfirmListsTheSteps() {
        var proposed: Pair<String, Action>? = null
        compose.setContent { AppRow(onHome.copy(removable = true), emptyList(), null, busy = false, actions = DeviceActions(propose = { p, a -> proposed = p to a })) }
        compose.onNodeWithText("Projectivy Launcher").performClick()
        compose.onNodeWithText(Copy.button(Action.Uninstall)).performClick()
        assertEquals(pkg to Action.Uninstall, proposed)
    }

    @Test fun theHomeUninstallConfirmSaysWhatHappensFirst() {
        compose.setContent { UninstallDialog(onHome.copy(removable = true), onUninstall = {}, onDismiss = {}) }
        compose.onNodeWithText(Copy.uninstallHome("Projectivy Launcher", "Google TV Home", service = true)).assertExists()
        assertTrue("Turns off Projectivy Launcher's accessibility service" in Copy.uninstallHome("Projectivy Launcher", "Google TV Home", service = true))
        assertFalse("accessibility" in Copy.uninstallHome("FLauncher", "Google TV Home", service = false))
    }

    @Test fun aLauncherOnHomeTheTvCameWithOffersNoUninstall() {
        compose.setContent { AppRow(onHome, emptyList(), null, busy = false, actions = DeviceActions()) }
        compose.onNodeWithText("Projectivy Launcher").performClick()
        compose.onNodeWithText(Copy.button(Action.Uninstall)).assertDoesNotExist()
    }

    @Test fun theRowSaysTheServiceIsOffAndOffersTurnOn() {
        var on: String? = null
        compose.setContent { AppRow(onHome, emptyList(), null, busy = false, actions = DeviceActions(homeServiceOn = { on = it })) }
        compose.onNodeWithText(Copy.SERVICE_OFF).assertExists()
        compose.onNodeWithText("Projectivy Launcher").performClick()
        compose.onNodeWithText(Copy.TURN_ON).performClick()
        // Not now leaves it off; Turn it on asks the TV.
        compose.onNodeWithText(Copy.NOT_NOW).performClick()
        assertEquals(null, on)
        compose.onNodeWithText(Copy.TURN_ON).performClick()
        compose.onNodeWithText(Copy.TURN_IT_ON).performClick()
        assertEquals(pkg, on)
    }

    @Test fun theCardsGotItAndTurnOn() {
        var dismissed = false
        var on = false
        compose.setContent { ServiceOffCard(onHome, onTurnOn = { on = true }, onDismiss = { dismissed = true }) }
        compose.onNodeWithText(Copy.serviceOffTitle("Google TV Home")).assertExists()
        compose.onNodeWithText(Copy.TURN_ON).performClick()
        assertFalse(on)
        compose.onNodeWithText(Copy.TURN_IT_ON).performClick()
        assertTrue(on)
        compose.onNodeWithText(Copy.GOT_IT).performClick()
        assertTrue(dismissed)
    }
}
