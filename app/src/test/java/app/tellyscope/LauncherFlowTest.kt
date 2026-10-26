// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewModelScope
import app.tellyscope.data.AppInfo
import app.tellyscope.data.Device
import app.tellyscope.data.DeviceSource
import app.tellyscope.data.DeviceState
import app.tellyscope.data.FakeDeviceSource
import app.tellyscope.data.HistoryEntry
import app.tellyscope.data.ScanHit
import app.tellyscope.data.ScanKind
import app.tellyscope.data.Snapshot
import app.tellyscope.data.StoreState
import app.tellyscope.data.Suggestions
import app.tellyscope.ui.Copy
import app.tellyscope.ui.LauncherActions
import app.tellyscope.ui.LauncherCard
import app.tellyscope.ui.LauncherFlow
import app.tellyscope.ui.LauncherScreen
import app.tellyscope.ui.PLAY_STORE
import app.tellyscope.ui.STORE_POLL_MS
import app.tellyscope.ui.TellyscopeViewModel
import app.tellyscope.ui.offerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TV_HOME = "com.google.android.tvlauncher"
private const val PROJECTIVY = "com.spocky.projengmenu"
private const val FLAUNCHER = "me.efesser.flauncher"

private val store = AppInfo(PLAY_STORE, "Google Play Store", null, true, true, null, "1.0")
private val stock = AppInfo(TV_HOME, "Google TV Home", null, true, true, "this is your home screen", "1.0", home = true)

/** One online TV whose Play Store answers from a script. */
private class StoreTv : DeviceSource by FakeDeviceSource(FakeDeviceSource.Scenario.Empty, clock = { 0L }) {
    var opens = true
    val opened = mutableListOf<String>()
    var states = ArrayDeque<StoreState>()
    var installed = false
    var hidden = false
    val switched = mutableListOf<Pair<String, Boolean>>()

    override suspend fun known() = listOf(Device("tv", ScanHit("tv", 5555, ScanKind.Classic, "tv", null), DeviceState.Online, null, null, null, 0L))
    override fun live(deviceId: String): Flow<Snapshot> = emptyFlow()
    override suspend fun suggestions(deviceId: String) = Suggestions.Unavailable
    override suspend fun history(deviceId: String) = emptyList<HistoryEntry>()

    override suspend fun apps(deviceId: String) = listOf(store, stock) +
        if (installed) listOf(AppInfo(PROJECTIVY, "Projectivy Launcher", null, false, true, null, "1.0", homeInstead = TV_HOME, homeService = true)) else emptyList()

    override suspend fun openStore(deviceId: String, packageName: String): Boolean {
        opened += packageName
        return opens
    }

    override suspend fun storeState(deviceId: String, packageName: String): StoreState {
        val s = states.removeFirstOrNull() ?: StoreState.InStore
        if (s == StoreState.Installed) installed = true
        return s
    }

    override suspend fun launcherCardHidden(deviceId: String) = hidden

    override suspend fun hideLauncherCard(deviceId: String) {
        hidden = true
    }

    override suspend fun makeHome(deviceId: String, packageName: String, service: Boolean): HistoryEntry {
        switched += packageName to service
        return HistoryEntry(1, deviceId, 0L, app.tellyscope.data.Action.Home, packageName, "Projectivy Launcher", app.tellyscope.data.Action.Home, null)
    }
}

/** Install a home screen: the Store wait, its dead ends, and the card's Not now. */
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val test = TestScope(dispatcher)
    private val source = StoreTv()
    private lateinit var vm: TellyscopeViewModel

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.started(): TellyscopeViewModel {
        vm = TellyscopeViewModel(source, clock = { 0L })
        runCurrent()
        vm.openLauncherFlow()
        return vm
    }

    private val step get() = vm.state.value.device.launcher?.step

    @Test fun itWaitsForTheInstallThenOffersTheSwitch() = with(test) {
        source.states = ArrayDeque(listOf(StoreState.InStore, StoreState.Installed))
        started().installLauncher(PROJECTIVY)
        runCurrent()
        assertEquals(listOf(PROJECTIVY), source.opened)
        assertEquals(LauncherFlow.Step.Waiting, step)
        advanceTimeBy(STORE_POLL_MS + 1)
        assertEquals(LauncherFlow.Step.Waiting, step)
        advanceTimeBy(STORE_POLL_MS)
        assertEquals(LauncherFlow.Step.Installed, step)
        assertTrue(vm.state.value.device.apps.any { it.packageName == PROJECTIVY })

        vm.switchToLauncher(service = false)
        runCurrent()
        assertNull(step)
        assertEquals(listOf(PROJECTIVY to false), source.switched)
    }

    @Test fun aStoreThatClosesWithoutTheInstallSaysSo() = with(test) {
        source.states = ArrayDeque(listOf(StoreState.InStore, StoreState.Elsewhere))
        started().installLauncher(PROJECTIVY)
        advanceTimeBy(2 * STORE_POLL_MS + 1)
        assertEquals(LauncherFlow.Step.Left, step)
        advanceTimeBy(10 * STORE_POLL_MS)
        assertEquals(LauncherFlow.Step.Left, step) // stopped checking
    }

    @Test fun aStoreThatDidntOpenSaysSoAndStillCatchesAnInstallByHand() = with(test) {
        source.opens = false
        source.states = ArrayDeque(listOf(StoreState.Elsewhere, StoreState.Installed))
        started().installLauncher(PROJECTIVY)
        runCurrent()
        assertEquals(LauncherFlow.Step.NoStore, step)
        advanceTimeBy(STORE_POLL_MS + 1)
        assertEquals(LauncherFlow.Step.NoStore, step) // never on screen, so not "closed"
        advanceTimeBy(STORE_POLL_MS)
        assertEquals(LauncherFlow.Step.Installed, step)
    }

    @Test fun notNowAfterTheInstallSaysWhereToSwitchLater() = with(test) {
        source.states = ArrayDeque(listOf(StoreState.Installed))
        started().installLauncher(PROJECTIVY)
        advanceTimeBy(STORE_POLL_MS + 1)
        vm.launcherLater()
        assertNull(step)
        assertEquals(Copy.launcherLater("Projectivy Launcher"), vm.notice.value)
        assertTrue(source.switched.isEmpty())
    }

    @Test fun closingStopsTheWait() = with(test) {
        started().installLauncher(PROJECTIVY)
        runCurrent()
        vm.closeLauncherFlow()
        source.states = ArrayDeque(listOf(StoreState.Installed))
        advanceTimeBy(5 * STORE_POLL_MS)
        assertNull(step)
        assertFalse(source.installed)
    }

    @Test fun notNowPutsTheCardAwayForThatTv() = with(test) {
        vm = TellyscopeViewModel(source, clock = { 0L })
        runCurrent()
        assertFalse(vm.state.value.device.launcherCardHidden)
        vm.hideLauncherCard()
        runCurrent()
        assertTrue(vm.state.value.device.launcherCardHidden)
        assertTrue(source.hidden)
    }
}

/** What the screens offer at each step. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LauncherScreenTest {
    @get:Rule val compose = createComposeRule()

    private val tv = Device("tv", ScanHit("tv", 5555, ScanKind.Classic, "tv", null), DeviceState.Online, null, null, null, 0L)

    @Test fun theCardShowsOnlyWithAStoreAndNoSecondLauncher() {
        assertTrue(offerLauncher(listOf(store, stock), hidden = false))
        assertFalse(offerLauncher(listOf(store, stock), hidden = true))
        assertFalse(offerLauncher(listOf(stock), hidden = false)) // Fire TV: no Play Store
        val other = AppInfo(PROJECTIVY, "Projectivy Launcher", null, false, true, null, "1.0", homeInstead = TV_HOME)
        assertFalse(offerLauncher(listOf(store, stock, other), hidden = false))
    }

    @Test fun theCardsTwoButtons() {
        var notNow = false
        var chose = false
        compose.setContent { LauncherCard("Google TV Home", onNotNow = { notNow = true }, onChoose = { chose = true }) }
        compose.onNodeWithText(Copy.NOT_NOW).performClick()
        compose.onNodeWithText(Copy.CHOOSE_ONE).performClick()
        assertTrue(notNow && chose)
    }

    @Test fun thePickedLauncherIsTheOneInstalled() {
        val installs = mutableListOf<String>()
        compose.setContent { LauncherScreen(tv, LauncherFlow(), listOf(store, stock), LauncherActions(install = { installs += it })) }
        compose.onNodeWithText("FLauncher").performScrollTo().performClick() // under the bottom bar on a small screen
        compose.onNodeWithText(Copy.OPEN_IN_STORE).performClick()
        assertEquals(listOf(FLAUNCHER), installs)
    }

    @Test fun theStoreDidntOpenOffersAnotherOrARetry() {
        val installs = mutableListOf<String>()
        val picks = mutableListOf<String>()
        compose.setContent {
            LauncherScreen(tv, LauncherFlow(LauncherFlow.Step.NoStore, PROJECTIVY), listOf(store, stock), LauncherActions(install = { installs += it }, chooseAnother = { picks += it }))
        }
        compose.onNodeWithText(Copy.NO_STORE).assertExists()
        compose.onNodeWithText(Copy.TRY_AGAIN).performClick()
        compose.onNodeWithText(Copy.CHOOSE_ANOTHER).performClick()
        assertEquals(listOf(PROJECTIVY), installs)
        assertEquals(listOf(PROJECTIVY), picks)
    }

    @Test fun yesOnAServiceLauncherAsksAboutTheService() {
        val switches = mutableListOf<Boolean>()
        val projectivy = AppInfo(PROJECTIVY, "Projectivy Launcher", null, false, true, null, "1.0", homeInstead = TV_HOME, homeService = true)
        compose.setContent {
            LauncherScreen(tv, LauncherFlow(LauncherFlow.Step.Installed, PROJECTIVY), listOf(store, stock, projectivy), LauncherActions(switch = { switches += it }))
        }
        compose.onNodeWithText(Copy.MAKE_IT_HOME).assertExists()
        compose.onNodeWithText(Copy.confirm(app.tellyscope.data.Action.Home)).performClick()
        compose.onNodeWithText(Copy.serviceTitle("Projectivy Launcher")).assertExists()
        compose.onNodeWithText(Copy.TURN_IT_ON).performClick()
        assertEquals(listOf(true), switches)
    }
}
