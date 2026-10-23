// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.tellyscope.adb.ApkInfo
import app.tellyscope.adb.InstallRefusal
import app.tellyscope.adb.InstalledVersion
import app.tellyscope.adb.Progress
import app.tellyscope.adb.TvFile
import app.tellyscope.adb.TvFolder
import app.tellyscope.data.Action
import app.tellyscope.data.AppInfo
import app.tellyscope.data.AppNames
import app.tellyscope.data.Device
import app.tellyscope.data.DeviceState
import app.tellyscope.data.FakeDeviceSource
import app.tellyscope.data.HistoryEntry
import app.tellyscope.data.ListEntry
import app.tellyscope.data.PickedApp
import app.tellyscope.data.ScanHit
import app.tellyscope.data.ScanKind
import app.tellyscope.data.ScanProgress
import app.tellyscope.data.Suggested
import app.tellyscope.data.SuggestedGroup
import app.tellyscope.data.Suggestions
import app.tellyscope.ui.AdvancedScreen
import app.tellyscope.ui.DebloatScreen
import app.tellyscope.ui.DeviceActions
import app.tellyscope.ui.DeviceScreen
import app.tellyscope.ui.Copy
import app.tellyscope.ui.DeviceUi
import app.tellyscope.ui.DisableDialog
import app.tellyscope.ui.ForgetDialog
import app.tellyscope.ui.FilesBlock
import app.tellyscope.ui.FilesUi
import app.tellyscope.ui.RestoreDialog
import app.tellyscope.ui.PermissionScreen
import app.tellyscope.ui.RebootDialog
import app.tellyscope.ui.RemoteActions
import app.tellyscope.ui.RemoteScreen
import app.tellyscope.ui.Restart
import app.tellyscope.ui.RowState
import app.tellyscope.ui.ScanScreen
import app.tellyscope.ui.ScanUi
import app.tellyscope.ui.SideloadUi
import app.tellyscope.ui.TransferUi
import app.tellyscope.ui.UiState
import app.tellyscope.ui.theme.TellyscopeTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen on fixed fake data. `./gradlew :app:recordRoborazziDebug`
 * writes PNGs to app/build/outputs/roborazzi; `verifyRoborazziDebug` diffs them.
 * Dynamic color is off so the branded fallback scheme is what gets reviewed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val now = 1_760_000_000_000L
    private val fake = FakeDeviceSource(clock = { now }, random = Random(7))

    private val xgimi = ScanHit("192.168.1.23", 5555, ScanKind.Classic, "xgimi-projector", "XGIMI")
    private val box = ScanHit("192.168.1.41", 5555, ScanKind.Classic, "android-7f3a", "Amlogic")
    private val chromecast = ScanHit("192.168.1.57", 37211, ScanKind.Wireless, "chromecast", "Google")
    private val unknown = ScanHit("192.168.1.88", 5555, ScanKind.Classic, null, null)

    /**
     * [screen] captures every window, so a dialog shows over its scrim. Robolectric doesn't apply
     * the platform dialog width, so dialogs still render edge to edge here; on a phone the
     * default `DialogProperties` give them the usual margins.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun shot(name: String, dark: Boolean = false, screen: Boolean = false, before: () -> Unit = {}, content: @Composable () -> Unit) {
        // Expressive indicators animate forever, so the clock never idles on its own:
        // drive it by hand and capture a fixed frame.
        compose.mainClock.autoAdvance = false
        compose.setContent { TellyscopeTheme(dark = dark, dynamic = false) { content() } }
        compose.mainClock.advanceTimeBy(1_000)
        before()
        compose.mainClock.advanceTimeBy(1_000)
        val path = "build/outputs/roborazzi/${name}${if (dark) "_dark" else ""}.png"
        if (screen) captureScreenRoboImage(path) else compose.onRoot().captureRoboImage(path)
    }

    private fun scan(ui: ScanUi, dark: Boolean = false, name: String) = shot(name, dark) {
        ScanScreen(ui, now, {}, {}, { _, _ -> }, {}, { null })
    }

    @Test fun scan_inProgress() = scan(
        ScanUi(
            scanning = true,
            progress = ScanProgress(115, 254, "192.168.1.0/24"),
            hits = listOf(xgimi, box),
            rows = mapOf(xgimi.ip to RowState.Found, box.ip to RowState.Found),
        ),
        name = "01_scan_in_progress",
    )

    private val connectStates get() = ScanUi(
        scanning = false,
        progress = ScanProgress(254, 254, "192.168.1.0/24"),
        hits = listOf(xgimi, chromecast, unknown),
        rows = mapOf(
            xgimi.ip to RowState.Waiting(now - 12_000),
            chromecast.ip to RowState.Code("4821"),
            unknown.ip to RowState.NotAllowed,
        ),
    )

    @Test fun scan_connectStates() = scan(connectStates, name = "02_scan_connect_states")

    @Test fun scan_connectStates_dark() = scan(connectStates, dark = true, name = "02_scan_connect_states")

    @Test fun scan_empty() = scan(
        ScanUi(scanning = false, progress = ScanProgress(254, 254, "192.168.1.0/24")),
        name = "03_scan_empty_guide",
    )

    private fun deviceState(device: DeviceUi.() -> DeviceUi = { this }): UiState = runBlocking {
        val d = Device(xgimi.ip, xgimi, DeviceState.Online, "XGIMI Horizon Pro", "XGIMI", "11", now)
        UiState(
            devices = listOf(d) + fake.known(),
            selectedId = d.id,
            device = DeviceUi(
                snapshot = fake.snapshot(d.id),
                apps = fake.apps(d.id).map { it.copy(icon = sampleIcons[it.packageName]) },
                network = fake.network(d.id),
                lists = lists(d.id),
            ).device(),
            now = now,
        )
    }

    /** The fake's list, among a few other models so "Which TV is this?" has a choice. */
    private suspend fun lists(id: String): Suggestions.Ready {
        val ready = fake.suggestions(id) as Suggestions.Ready
        val others = listOf("Sony Bravia (Google TV)", "TCL C-series", "Xiaomi Mi Box S").map {
            val slug = it.lowercase().replace(Regex("[^a-z]+"), "-").trim('-')
            ListEntry(slug, it, null, 30, "devices/$slug.json", "0".repeat(64))
        }
        return ready.copy(devices = ready.devices + others)
    }

    @Test fun device_overview() {
        shot("04_device") { DeviceScreen(deviceState(), DeviceActions()) }
        // Plain words on the main page: no Details expander, no MB per app; numbers live on Advanced.
        compose.onNodeWithText("Details").assertDoesNotExist()
        compose.onNodeWithText("Advanced").assertExists()
        compose.onNodeWithText("Running · ", substring = true).assertDoesNotExist()
        // The three main actions sit under the system widget, Debloat among them.
        for (t in listOf("Install app", "Debloat", "Send file")) compose.onNodeWithText(t).assertIsEnabled()
        for (t in listOf("Up for", "Last software update", "Internal storage", "Network")) compose.onNodeWithText(t).assertExists()
    }

    @Test fun device_overview_dark() = shot("04_device", dark = true) { DeviceScreen(deviceState(), DeviceActions()) }

    @Test fun device_picker() = shot("04_device_picker", screen = true, before = {
        compose.onNodeWithContentDescription("Switch TV").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Add a TV").assertExists()
        Unit
    }) { DeviceScreen(deviceState().copy(showOffline = true), DeviceActions()) }

    @Config(qualifiers = "w411dp-h1500dp-xxhdpi")
    @Test fun device_confirmDisable() {
        val state = deviceState { copy(pending = "com.xgimi.assistant" to Action.Disable) }
        shot("05_device_confirm_disable") { DeviceScreen(state, DeviceActions()) }
    }

    @Test fun device_offline() {
        val base = deviceState()
        val off = base.devices.last()
        // The last stats this phone saw, 14 minutes before it went away.
        val last = fake.snapshot(off.id).copy(at = now - 14 * 60_000)
        shot("06_device_offline") {
            DeviceScreen(base.copy(selectedId = off.id, device = DeviceUi(snapshot = last), showOffline = true), DeviceActions())
        }
        // Old numbers don't speak for a TV that's away; what it is still does.
        compose.onNodeWithText("Up for").assertDoesNotExist()
        compose.onNodeWithText("Internal storage").assertDoesNotExist()
        compose.onNodeWithText("Last software update").assertExists()
    }

    @Test fun device_storageFull() {
        val state = deviceState { copy(snapshot = snapshot?.copy(storageUsedGb = 29.1, storageTotalGb = 32.0)) }
        shot("13_device_storage_full") { DeviceScreen(state, DeviceActions()) }
    }

    // Tall enough that History is composed without scrolling: the lazy list never lays it out otherwise.
    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    @Test fun device_history() {
        val history = listOf(
            HistoryEntry(2, xgimi.ip, now - 40_000, Action.Disable, "com.xgimi.ota", "XGIMI Updates", Action.Enable, null),
            HistoryEntry(1, xgimi.ip, now - 5 * 60_000, Action.Stop, "com.netflix.ninja", "Netflix", Action.Open, now - 4 * 60_000),
        )
        val openHistory = {
            compose.onNodeWithText("Apps").performClick() // fold the long list away
            compose.onNodeWithText("History").performClick()
            Unit
        }
        shot("07_device_history", before = openHistory) { DeviceScreen(deviceState { copy(history = history) }, DeviceActions()) }
    }

    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    @Test fun history_undoOfTurnOnAsksFirst() {
        val turnedOn = HistoryEntry(3, xgimi.ip, now - 20_000, Action.Enable, "com.xgimi.assistant", "XGIMI Assistant", Action.Disable, null)
        val undone = mutableListOf<HistoryEntry>()
        val openAndTap = {
            compose.onNodeWithText("Apps").performClick()
            compose.onNodeWithText("History").performClick()
            compose.mainClock.advanceTimeBy(1_000)
            // The button says what it does, and a tap only asks.
            compose.onNodeWithText("Disable").performClick()
            Unit
        }
        shot("16_history_undo_disable", screen = true, before = openAndTap) {
            DeviceScreen(deviceState { copy(history = listOf(turnedOn)) }, DeviceActions(undo = { undone += it }))
        }
        compose.onNodeWithText("Disable XGIMI Assistant?").assertExists()
        assertEquals(emptyList<HistoryEntry>(), undone)
        compose.onNodeWithText("Yes, disable it").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf(turnedOn), undone)
    }

    /** A real TV's app list: no per-process stats yet, so the apps sit right under the stat grid. */
    private fun appsState(apps: List<AppInfo>): UiState {
        val base = deviceState()
        return base.copy(device = base.device.copy(snapshot = base.device.snapshot?.copy(procs = emptyList()), apps = apps))
    }

    private val tvApps = listOf(
        "com.google.android.youtube.tv" to 0xFFE53935,
        "com.netflix.ninja" to 0xFF212121,
        "org.jellyfin.androidtv" to 0xFF7B5EA7,
        "com.example.weatherbox" to 0xFF0288D1,
        "com.xgimi.assistant" to 0xFF00897B,
    )

    /** Stand-ins for the PNGs the helper reads off the TV: no brand artwork in the repo. */
    private fun iconPng(argb: Long): ByteArray {
        val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        Canvas(b).apply {
            drawColor(argb.toInt())
            drawCircle(48f, 48f, 24f, Paint().apply { color = android.graphics.Color.WHITE; isAntiAlias = true })
        }
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun app(pkg: String, label: String, icon: ByteArray?) =
        AppInfo(pkg, label, null, pkg.startsWith("com.xgimi"), true, null, "", icon)

    /**
     * Generic icons for the sample apps, so device shots look like a real TV's list. Each is the
     * app's avatar colour with a plain glyph: no brand artwork in the repo, test-only.
     */
    private val sampleIcons: Map<String, ByteArray> by lazy {
        val white = Paint().apply { color = android.graphics.Color.WHITE; isAntiAlias = true }
        val line = Paint(white).apply { style = Paint.Style.STROKE; strokeWidth = 7f; strokeCap = Paint.Cap.ROUND }
        fun icon(argb: Long, glyph: Canvas.() -> Unit): ByteArray {
            val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            Canvas(b).apply { drawColor(argb.toInt()); glyph() }
            return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
        fun Canvas.squares() = listOf(26f, 52f).forEach { x -> listOf(26f, 52f).forEach { y -> drawRoundRect(x, y, x + 18f, y + 18f, 4f, 4f, white) } }
        fun Canvas.screen() { drawRoundRect(22f, 26f, 74f, 62f, 6f, 6f, line); drawLine(38f, 72f, 58f, 72f, line) }
        fun Canvas.film() {
            drawRoundRect(30f, 22f, 66f, 74f, 4f, 4f, white)
            val hole = Paint().apply { color = 0xFF141414.toInt() }
            listOf(28f, 40f, 52f, 64f).forEach { y -> drawRect(33f, y, 38f, y + 5f, hole); drawRect(58f, y, 63f, y + 5f, hole) }
        }
        fun Canvas.stack() = listOf(28f, 44f, 60f).forEach { y -> drawRoundRect(24f, y, 72f, y + 10f, 5f, 5f, white) }
        fun Canvas.mic() { drawRoundRect(38f, 20f, 58f, 52f, 10f, 10f, white); drawArc(30f, 34f, 66f, 64f, 0f, 180f, false, line); drawLine(48f, 66f, 48f, 76f, line) }
        fun Canvas.arrow() { drawLine(48f, 22f, 48f, 62f, line); drawLine(34f, 48f, 48f, 62f, line); drawLine(62f, 48f, 48f, 62f, line); drawLine(28f, 74f, 68f, 74f, line) }
        fun Canvas.star() = drawPath(
            android.graphics.Path().apply {
                (0 until 10).forEach { i ->
                    val r = if (i % 2 == 0) 28f else 12f
                    val a = Math.toRadians(i * 36.0 - 90)
                    val x = 48f + r * Math.cos(a).toFloat()
                    val y = 50f + r * Math.sin(a).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            },
            white,
        )
        fun Canvas.gear() { drawCircle(48f, 48f, 20f, line); drawCircle(48f, 48f, 6f, white) }
        fun Canvas.dots() = listOf(30f, 48f, 66f).forEach { drawCircle(it, 48f, 6f, white) }
        fun Canvas.keys() {
            drawRoundRect(20f, 32f, 76f, 66f, 6f, 6f, line)
            listOf(32f, 44f, 56f, 64f).forEach { x -> drawCircle(x, 44f, 3f, white) }
            drawLine(36f, 56f, 60f, 56f, line)
        }
        mapOf(
            "com.spocky.projengmenu" to icon(0xFF3D5AFE) { squares() },
            "com.google.android.youtube.tv" to icon(0xFFC4302B) { screen() },
            "com.netflix.ninja" to icon(0xFF141414) { film() },
            "org.jellyfin.androidtv" to icon(0xFF7B5EA7) { stack() },
            "com.xgimi.assistant" to icon(0xFF00897B) { mic() },
            "com.xgimi.ota" to icon(0xFF00838F) { arrow() },
            "com.google.android.tvrecommendations" to icon(0xFFF9A825) { star() },
            "com.android.systemui" to icon(0xFF546E7A) { dots() },
            "com.android.tv.settings" to icon(0xFF607D8B) { gear() },
            "com.google.android.gms" to icon(0xFF1E88E5) { gear() },
            "com.google.android.inputmethod.latin" to icon(0xFF5C6BC0) { keys() },
        )
    }

    @Test fun apps_helperNames() {
        val labels = listOf("YouTube", "Netflix", "Jellyfin", "Weather Box", "XGIMI Assistant")
        val apps = tvApps.zip(labels) { (pkg, color), label -> app(pkg, label, iconPng(color)) }
        shot("09_apps_helper_names") { DeviceScreen(appsState(apps), DeviceActions()) }
    }

    @Test fun apps_fallbackNames() {
        // The helper was refused: bundled names where known, a humanised id otherwise, letter avatars.
        val apps = tvApps.map { (pkg, _) -> app(pkg, AppNames.fallback(pkg), null) }
        shot("10_apps_fallback_names") { DeviceScreen(appsState(apps), DeviceActions()) }
    }

    /** The Files section's body on its own: the section is collapsed on the full page. */
    @Test fun device_files() = shot("09_device_files") {
        val files = runBlocking { fake.files(xgimi.ip, TvFolder.DOWNLOAD) }
        Surface { Column(Modifier.padding(16.dp)) { FilesBlock(FilesUi(TvFolder.DOWNLOAD, files), now, DeviceActions()) } }
    }

    private val dir = Files.createTempDirectory("shots").toFile()
    private fun apk(name: String, bytes: Long) = File(dir, name).also { RandomAccessFile(it, "rw").use { f -> f.setLength(bytes) } }
    private val kodi = PickedApp(listOf(apk("kodi.apk", 74_000_000)), ApkInfo("org.xbmc.kodi", 2101, "21.1"), "Kodi")
    private val split = PickedApp(
        listOf(apk("base.apk", 31_000_000), apk("split_config.armeabi_v7a.apk", 9_400_000), apk("split_config.xxhdpi.apk", 1_100_000)),
        ApkInfo("com.example.player", 410, "4.1.0"),
        "Example Player",
    )

    private fun sideload(name: String, s: SideloadUi, dark: Boolean = false) =
        shot(name, dark, screen = true) { DeviceScreen(deviceState { copy(sideload = s) }, DeviceActions()) }

    @Test fun sideload_new() = sideload("10_sideload_new", SideloadUi(SideloadUi.Step.Ready, split, null))

    @Test fun sideload_update() =
        sideload("10_sideload_update", SideloadUi(SideloadUi.Step.Ready, kodi, InstalledVersion(2050, "20.5")), dark = true)

    @Test fun sideload_downgrade() =
        sideload("11_sideload_downgrade", SideloadUi(SideloadUi.Step.Ready, kodi, InstalledVersion(2200, "22.0")))

    @Test fun sideload_installing() = sideload(
        "12_sideload_installing",
        SideloadUi(SideloadUi.Step.Installing, kodi, null, progress = Progress(30_000_000, 74_000_000)),
    )

    @Test fun sideload_refused() = sideload(
        "13_sideload_refused",
        SideloadUi(
            SideloadUi.Step.Failed, kodi, InstalledVersion(2050, "20.5"),
            error = Copy.installRefused(InstallRefusal.SIGNATURE, "Kodi"),
            detail = "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package org.xbmc.kodi signatures do not match previously installed version; ignoring!]",
        ),
    )

    @Test fun sideload_installed() = sideload("14_sideload_installed", SideloadUi(SideloadUi.Step.Installed, kodi, null))

    @Test fun transfer() = shot("15_transfer", screen = true) {
        val t = TransferUi("holiday-2025.mp4", toTv = false, folder = TvFolder.DOWNLOAD, progress = Progress(301_000_000, 812_000_000))
        DeviceScreen(deviceState { copy(transfer = t) }, DeviceActions())
    }

    @Test fun permission() = shot("08_permission") { PermissionScreen(denied = false, canAsk = true, {}, {}) }

    @Test fun permission_denied() = shot("08_permission_denied") { PermissionScreen(denied = true, canAsk = true, {}, {}) }

    @Test fun forget() = shot("09_forget", screen = true) { ForgetDialog("xgimi-projector", {}, {}) }

    @Test fun scan_offWifi() = scan(
        ScanUi(scanning = false, noLan = true, hits = listOf(chromecast), rows = mapOf(chromecast.ip to RowState.Found)),
        name = "10_scan_off_wifi",
    )

    @Test fun device_notAllowed() {
        val base = deviceState()
        val off = base.devices.last().copy(state = DeviceState.NotAllowed)
        shot("12_device_not_allowed") {
            DeviceScreen(base.copy(devices = base.devices.dropLast(1) + off, selectedId = off.id, device = DeviceUi()), DeviceActions())
        }
    }

    @Test fun restore() = shot("11_restore", screen = true) { RestoreDialog(2, {}, {}) }

    @Test fun remote() = shot("12_remote") { RemoteScreen(deviceState(), RemoteActions()) }

    @Test fun remote_dark() = shot("12_remote", dark = true) { RemoteScreen(deviceState(), RemoteActions()) }

    @Test fun remote_restarting() {
        val state = deviceState().let { it.copy(restarting = mapOf(it.selectedId!! to Restart(now - 25_000, down = true))) }
        shot("13_remote_restarting") { RemoteScreen(state, RemoteActions()) }
    }

    @Test fun device_restarting() {
        val state = deviceState().let { it.copy(restarting = mapOf(it.selectedId!! to Restart(now - 200_000, down = true))) }
        shot("14_device_restarting") { DeviceScreen(state, DeviceActions()) }
    }

    @Test fun reboot() = shot("15_reboot", screen = true) { RebootDialog({}, {}) }

    @Config(qualifiers = "w411dp-h2400dp-xxhdpi")
    @Test fun advanced() = shot("17_advanced") { AdvancedScreen(deviceState(), back = {}) }

    @Test fun debloat_pick() {
        val lists = deviceState().device.lists
        shot("18_debloat_pick") { DebloatScreen(lists, {}, {}, {}) }
        compose.onNodeWithText("Next").assertIsEnabled() // the matched list is already chosen
    }

    @Test fun debloat_unknownModel() {
        val ready = deviceState().device.lists as Suggestions.Ready
        shot("18_debloat_pick_unknown") { DebloatScreen(Suggestions.Pick(null, ready.devices), {}, {}, {}) }
        compose.onNodeWithText("Next").assertIsNotEnabled() // nothing guessed for a model it doesn't know
    }

    @Test fun debloat_review() {
        val lists = deviceState().device.lists as Suggestions.Ready
        val next = { compose.onNodeWithText("Next").performClick(); Unit }
        shot("19_debloat_review", before = next) { DebloatScreen(lists, {}, {}, {}) }
        val apps = lists.groups.flatMap { it.packages }
        for (a in apps) compose.onNodeWithText(a.label).assertExists()
        val ticked = apps.count { it.ticked }
        compose.onNodeWithText("Disable $ticked ${if (ticked == 1) "app" else "apps"}").assertIsEnabled()
    }

    @Test fun debloat_review_otherAndroid() {
        // A list tested on another Android version ticks nothing, the way the list builder hands it over.
        val lists = (deviceState().device.lists as Suggestions.Ready).let { r ->
            r.copy(sdk = 28, groups = r.groups.map { g -> g.copy(packages = g.packages.map { it.copy(ticked = false) }) })
        }
        val next = { compose.onNodeWithText("Next").performClick(); Unit }
        shot("19_debloat_review_other_android", before = next) { DebloatScreen(lists, {}, {}, {}) }
        compose.onNodeWithText("so nothing is ticked", substring = true).assertExists()
        compose.onNodeWithText("Disable 0 apps").assertIsNotEnabled()
    }

    @Test fun disableSuggested() {
        val apps = listOf(
            Suggested("com.xgimi.assistant", "XGIMI Assistant", null, optional = false, ticked = true),
            Suggested("com.google.android.tvrecommendations", "TV Recommendations", null, optional = false, ticked = true),
        )
        shot("16_disable_suggested", screen = true) { DisableDialog(apps, {}, {}) }
        // Names, not just a count.
        compose.onNodeWithText("Disable 2 apps?").assertExists()
        for (a in apps) {
            compose.onNodeWithText(a.label).assertExists()
            compose.onNodeWithText(a.pkg).assertExists()
        }
    }
    // README shots: the same screens with a fuller sample list and history than the tests above need.

    /** A fuller sample list for the README: two groups, one optional app left unticked. */
    private fun readmeLists(): Suggestions.Ready {
        val r = deviceState().device.lists as Suggestions.Ready
        return r.copy(
            groups = listOf(
                SuggestedGroup(
                    "Google extras",
                    listOf(
                        Suggested("com.google.android.tvrecommendations", "TV Recommendations", "home screen suggestions", optional = false, ticked = true),
                        Suggested("com.google.android.videos", "Google TV", "film and TV store", optional = false, ticked = true),
                        Suggested("com.google.android.play.games", "Google Play Games", null, optional = false, ticked = true),
                    ),
                ),
                SuggestedGroup(
                    "Extras",
                    listOf(
                        Suggested("com.xgimi.assistant", "XGIMI Assistant", "voice assistant", optional = false, ticked = true),
                        Suggested("com.netflix.ninja", "Netflix", null, optional = true, ticked = false),
                    ),
                ),
            ),
        )
    }

    @Test fun readme_debloat() {
        val next = { compose.onNodeWithText("Next").performClick(); Unit }
        shot("20_readme_debloat", before = next) { DebloatScreen(readmeLists(), {}, {}, {}) }
        compose.onNodeWithText("Disable 4 apps").assertIsEnabled()
    }

    @Test fun readme_confirm() {
        // Over the review step, as in the app.
        val confirm = {
            compose.onNodeWithText("Next").performClick()
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText("Disable 4 apps").performClick()
            Unit
        }
        shot("21_readme_confirm", screen = true, before = confirm) { DebloatScreen(readmeLists(), {}, {}, {}) }
        compose.onNodeWithText("Disable 4 apps?").assertExists()
    }

    // Tall, like device_history: frame.py shows it scrolled down to History under the top bar.
    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    @Test fun readme_history() {
        val history = listOf(
            HistoryEntry(4, xgimi.ip, now - 40_000, Action.Disable, "com.google.android.tvrecommendations", "TV Recommendations", Action.Enable, null),
            HistoryEntry(3, xgimi.ip, now - 45_000, Action.Disable, "com.xgimi.assistant", "XGIMI Assistant", Action.Enable, null),
            HistoryEntry(2, xgimi.ip, now - 5 * 60_000, Action.Stop, "com.netflix.ninja", "Netflix", Action.Open, null),
            HistoryEntry(1, xgimi.ip, now - 2 * 3_600_000, Action.Stop, "com.google.android.youtube.tv", "YouTube", Action.Open, now - 3_500_000),
        )
        val openHistory = {
            compose.onNodeWithText("Apps").performClick() // fold the long list away
            compose.onNodeWithText("History").performClick()
            Unit
        }
        shot("22_readme_history", before = openHistory) { DeviceScreen(deviceState { copy(history = history) }, DeviceActions()) }
        compose.onNodeWithText("Restore everything (2)").assertExists()
    }
    @Test fun readme_install() {
        // An update, since the sample TV already has Netflix: the dialog says what changes.
        val netflix = PickedApp(
            listOf(apk("base.apk", 38_000_000), apk("split_config.armeabi_v7a.apk", 7_200_000), apk("split_config.xxhdpi.apk", 1_400_000)),
            ApkInfo("com.netflix.ninja", 11_300, "11.3.0"),
            "Netflix",
        )
        sideload("23_readme_install", SideloadUi(SideloadUi.Step.Ready, netflix, InstalledVersion(11_200, "11.2.0")))
    }

    // Tall, like readme_history: frame.py scrolls it down to Files.
    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    @Test fun readme_files() {
        val files = listOf(
            TvFile("subtitles.srt", 61_000, now - 40 * 60_000L),
            TvFile("kodi-21.1-armeabi-v7a.apk", 74_000_000, now - 5 * 3_600_000L),
            TvFile("projectivy-backup.plbackup", 48_200, now - 26 * 3_600_000L),
            TvFile("holiday-2025.mp4", 812_000_000, now - 3 * 86_400_000L),
            TvFile("wallpaper-mountains.jpg", 2_400_000, now - 9 * 86_400_000L),
        )
        val openFiles = {
            compose.onNodeWithText("Apps").performClick() // fold the long list away
            compose.onNodeWithText("Files on the TV").performClick()
            Unit
        }
        shot("24_readme_files", before = openFiles) { DeviceScreen(deviceState { copy(files = FilesUi(TvFolder.DOWNLOAD, files)) }, DeviceActions()) }
        compose.onNodeWithText("wallpaper-mountains.jpg").assertExists()
    }

    @Test fun readme_send() = shot("25_readme_send", screen = true) {
        val t = TransferUi("family-trip-2025.mp4", toTv = true, folder = TvFolder.MOVIES, progress = Progress(486_000_000, 812_000_000))
        DeviceScreen(deviceState { copy(transfer = t) }, DeviceActions())
    }
}
