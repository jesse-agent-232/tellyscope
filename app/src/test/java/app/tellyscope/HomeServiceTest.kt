// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tellyscope.data.Action
import app.tellyscope.data.AppInfo
import app.tellyscope.ui.AppRow
import app.tellyscope.ui.Copy
import app.tellyscope.ui.DeviceActions
import app.tellyscope.ui.DeviceUi
import app.tellyscope.ui.HomeScreenBlock
import app.tellyscope.ui.ServiceOffCard
import app.tellyscope.ui.UninstallDialog
import app.tellyscope.ui.homeMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Home screen card: every launcher, Make Home with a confirm, and a launcher's accessibility
 * service as its own yes, never a side effect of the switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeServiceTest {
    @get:Rule val compose = createComposeRule()

    private val pkg = "com.spocky.projengmenu"
    private val stockPkg = "com.google.android.tvlauncher"
    private val stock = AppInfo(
        stockPkg, "Google TV Home", null, true, true, "this is your home screen", "1.0",
        home = true, answersHome = true, builtInLauncher = true,
    )
    private val projectivy = AppInfo(
        pkg, "Projectivy Launcher", null, false, true, null, "1.0",
        removable = true, answersHome = true, homeInstead = stockPkg, homeService = true,
    )
    private val flauncher = AppInfo("me.efesser.flauncher", "FLauncher", null, false, true, null, "1.0", removable = true, answersHome = true)

    /** Projectivy on Home, the stock launcher turned off by the switch to it. */
    private val onHome = projectivy.copy(
        homeInstead = null, home = true, homeBack = "Google TV Home", protectedReason = "this is your home screen",
    )
    private val stockOff = stock.copy(enabled = false, home = false, answersHome = false, protectedReason = null)

    private fun card(vararg apps: AppInfo, pending: Pair<String, Action>? = null, actions: DeviceActions = DeviceActions()) =
        compose.setContent { Column { HomeScreenBlock(DeviceUi(apps = apps.toList(), pending = pending), actions) } }

    private fun inDialog(text: String): SemanticsNodeInteraction = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    @Test fun theSwitchAsksAboutTheServiceSeparately() {
        val answers = mutableListOf<Boolean>()
        card(stock, projectivy, pending = pkg to Action.Home, actions = DeviceActions(confirmHome = { answers += it }))
        inDialog(Copy.makeHomeTitle("Projectivy Launcher")).assertExists()
        inDialog(Copy.MAKE_HOME).performClick()
        compose.onNodeWithText(Copy.serviceTitle("Projectivy Launcher")).assertExists()
        compose.onNodeWithText(Copy.SWITCH_WITHOUT).performClick()
        assertEquals(listOf(false), answers)
    }

    @Test fun turnItOnSwitchesWithTheService() {
        val answers = mutableListOf<Boolean>()
        card(stock, projectivy, pending = pkg to Action.Home, actions = DeviceActions(confirmHome = { answers += it }))
        inDialog(Copy.MAKE_HOME).performClick()
        compose.onNodeWithText(Copy.TURN_IT_ON).performClick()
        assertEquals(listOf(true), answers)
    }

    @Test fun aLauncherWithoutAServiceSwitchesStraightAway() {
        var confirmed = false
        card(stock, projectivy.copy(homeService = false), pending = pkg to Action.Home, actions = DeviceActions(confirm = { confirmed = true }))
        inDialog(Copy.explainReplace("Projectivy Launcher", "Google TV Home", stockPkg, service = false)).assertExists()
        inDialog(Copy.MAKE_HOME).performClick()
        compose.onNodeWithText(Copy.serviceTitle("Projectivy Launcher")).assertDoesNotExist()
        assertTrue(confirmed)
    }

    @Test fun theStockHomeIsCurrentAndAnAddedLauncherOffersMakeHomeAndUninstall() {
        val proposed = mutableListOf<Pair<String, Action>>()
        card(stock, projectivy, actions = DeviceActions(propose = { p, a -> proposed += p to a }))
        compose.onNodeWithText(Copy.opensOnHome("Google TV Home")).assertExists()
        compose.onNodeWithText(Copy.CURRENT).assertExists()
        compose.onNodeWithText(Copy.KEEPS_HOME).assertExists()
        compose.onNodeWithText(Copy.MAKE_HOME).performClick()
        compose.onNodeWithText(Copy.button(Action.Uninstall)).performClick()
        assertEquals(listOf(pkg to Action.Home, pkg to Action.Uninstall), proposed)
        // A launcher was added, so the card no longer offers to get one.
        compose.onNodeWithText(Copy.GET_LAUNCHER_TEXT).assertDoesNotExist()
    }

    @Test fun withNothingAddedTheCardOffersAnotherLauncher() {
        var installs = 0
        val store = AppInfo("com.android.vending", "Google Play Store", null, true, true, null, "1.0")
        card(stock, store, actions = DeviceActions(installLauncher = { installs++ }))
        compose.onNodeWithText(Copy.GET_LAUNCHER_TEXT).assertExists()
        compose.onNode(hasText(Copy.GET_LAUNCHER) and androidx.compose.ui.test.hasClickAction()).performClick()
        assertEquals(1, installs)
    }

    @Test fun withNoLauncherOfTheTvsOwnOnSetAsDefaultSaysNothingIsTurnedOff() {
        var confirmed = false
        val first = projectivy.copy(homeInstead = null, homeFirst = true, homeService = false)
        card(first, pending = pkg to Action.Home, actions = DeviceActions(confirm = { confirmed = true }))
        compose.onNodeWithText(Copy.NO_DEFAULT).assertExists()
        inDialog(Copy.explainFirstHome("Projectivy Launcher", pkg)).assertExists()
        inDialog(Copy.MAKE_HOME).performClick()
        assertTrue(confirmed)
    }

    @Test fun withNoDefaultTheTvsOwnCanBeMadeTheDefault() {
        // Android asks which home to use: the stock launcher is on, but nothing is the default.
        val asking = stock.copy(home = false)
        assertEquals(Action.Home, homeMove(asking, current = null))
        assertEquals(Action.Home, homeMove(projectivy, current = null))
    }

    @Test fun fromAnAddedLauncherTheTvsOwnGoesBackAndTurnsBackOn() {
        val proposed = mutableListOf<Pair<String, Action>>()
        card(onHome, stockOff, actions = DeviceActions(propose = { p, a -> proposed += p to a }))
        compose.onNodeWithText(Copy.builtIn(off = true)).assertExists()
        compose.onNodeWithText(Copy.MAKE_HOME).performClick()
        assertEquals(listOf(stockPkg to Action.HomeBack), proposed)
    }

    @Test fun makingTheTvsOwnHomeSaysTheAddedOnesServiceGoesOff() {
        var confirmed = false
        card(onHome, stockOff, pending = stockPkg to Action.HomeBack, actions = DeviceActions(confirm = { confirmed = true }))
        val text = Copy.explainLeave("Google TV Home", off = true, "Projectivy Launcher", currentService = true, service = false)
        inDialog(text).assertExists()
        assertTrue("turns off Projectivy Launcher's accessibility service" in text)
        assertTrue("turns Google TV Home back on" in text)
        inDialog(Copy.MAKE_HOME).performClick()
        assertTrue(confirmed)
    }

    @Test fun betweenAddedLaunchersNeitherIsTurnedOff() {
        val f = flauncher.copy(homeInstead = pkg)
        assertEquals(Action.Home, homeMove(f, onHome))
        card(onHome, f, stockOff, pending = f.packageName to Action.Home)
        val text = Copy.explainLeave("FLauncher", off = false, "Projectivy Launcher", currentService = true, service = false)
        inDialog(text).assertExists()
        assertTrue("Projectivy Launcher stays installed and on" in text)
        assertFalse("turns off FLauncher" in text)
    }

    @Test fun aSwitchWhoseTargetChangedIsCancelled() {
        // Make Home was asked, then the launcher was turned off in Debloat.
        var cancels = 0
        card(stock, projectivy.copy(enabled = false), pending = pkg to Action.Home, actions = DeviceActions(cancel = { cancels++ }))
        compose.waitForIdle()
        assertEquals(1, cancels)
        inDialog(Copy.MAKE_HOME).assertDoesNotExist()
    }

    @Test fun theHomeRowSaysTheServiceIsOffAndOffersTurnOn() {
        var on: String? = null
        card(onHome.copy(homeServiceOff = true), stockOff, actions = DeviceActions(homeServiceOn = { on = it }))
        compose.onNodeWithText(Copy.SERVICE_OFF).assertExists()
        compose.onNodeWithText(Copy.TURN_ON).performClick()
        // Not now leaves it off; Turn it on asks the TV.
        compose.onNodeWithText(Copy.NOT_NOW).performClick()
        assertNull(on)
        compose.onNodeWithText(Copy.TURN_ON).performClick()
        compose.onNodeWithText(Copy.TURN_IT_ON).performClick()
        assertEquals(pkg, on)
    }

    @Test fun uninstallOnTheHomeOneListsTheSteps() {
        card(onHome, stockOff, pending = pkg to Action.Uninstall)
        inDialog(Copy.uninstallHome("Projectivy Launcher", "Google TV Home", service = true)).assertExists()
    }

    @Test fun theHomeUninstallConfirmSaysWhatHappensFirst() {
        compose.setContent { UninstallDialog(onHome, onUninstall = {}, onDismiss = {}) }
        compose.onNodeWithText(Copy.uninstallHome("Projectivy Launcher", "Google TV Home", service = true)).assertExists()
        assertTrue("Turns off Projectivy Launcher's accessibility service" in Copy.uninstallHome("Projectivy Launcher", "Google TV Home", service = true))
        assertFalse("accessibility" in Copy.uninstallHome("FLauncher", "Google TV Home", service = false))
    }

    @Test fun anAppsRowForALauncherLinksToTheCardInsteadOfSwitching() {
        var opened = 0
        compose.setContent { AppRow(projectivy, emptyList(), null, busy = false, actions = DeviceActions(), toHomeScreen = { opened++ }) }
        compose.onNodeWithText("Projectivy Launcher").performClick()
        compose.onNodeWithText(Copy.MAKE_HOME).assertDoesNotExist()
        compose.onNodeWithText(Copy.HOME_SETTINGS).performClick()
        assertEquals(1, opened)
    }

    @Test fun theLauncherOnHomeKeepsUninstallInApps() {
        var proposed: Pair<String, Action>? = null
        compose.setContent { AppRow(onHome, emptyList(), null, busy = false, actions = DeviceActions(propose = { p, a -> proposed = p to a })) }
        compose.onNodeWithText("Projectivy Launcher").performClick()
        compose.onNodeWithText(Copy.HOME_SETTINGS).assertExists()
        compose.onNodeWithText(Copy.button(Action.Uninstall)).performClick()
        assertEquals(pkg to Action.Uninstall, proposed)
    }

    @Test fun aLauncherOnHomeTheTvCameWithOffersNoUninstall() {
        compose.setContent { AppRow(stock, emptyList(), null, busy = false, actions = DeviceActions()) }
        compose.onNodeWithText("Google TV Home").performClick()
        compose.onNodeWithText(Copy.HOME_SETTINGS).assertExists()
        compose.onNodeWithText(Copy.button(Action.Uninstall)).assertDoesNotExist()
    }

    @Test fun theCardsGotItAndTurnOn() {
        var dismissed = false
        var on = false
        val off = onHome.copy(homeServiceOff = true, homeServiceNote = true)
        compose.setContent { ServiceOffCard(off, onTurnOn = { on = true }, onDismiss = { dismissed = true }) }
        compose.onNodeWithText(Copy.serviceOffTitle("Google TV Home")).assertExists()
        compose.onNodeWithText(Copy.TURN_ON).performClick()
        assertFalse(on)
        compose.onNodeWithText(Copy.TURN_IT_ON).performClick()
        assertTrue(on)
        compose.onNodeWithText(Copy.GOT_IT).performClick()
        assertTrue(dismissed)
    }
}
