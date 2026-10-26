// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import androidx.lifecycle.viewModelScope
import app.tellyscope.data.Device
import app.tellyscope.data.DeviceSource
import app.tellyscope.data.DeviceState
import app.tellyscope.data.FakeDeviceSource
import app.tellyscope.data.ScanHit
import app.tellyscope.data.ScanKind
import app.tellyscope.data.Snapshot
import app.tellyscope.ui.Copy
import app.tellyscope.ui.TellyscopeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** One TV; [answer] is what clearing its text box does: true, false, or throw for a lost link. */
private class TextBox : DeviceSource by FakeDeviceSource(FakeDeviceSource.Scenario.Empty, clock = { 0L }) {
    var clears = 0
    var answer: () -> Boolean = { true }

    override suspend fun known() = listOf(
        Device("tv", ScanHit("tv", 5555, ScanKind.Classic, "tv", null), DeviceState.Online, null, null, null, 0L, nickname = "Projector"),
    )

    override fun live(deviceId: String) = emptyFlow<Snapshot>()

    override suspend fun clearText(deviceId: String): Boolean {
        clears++
        return answer()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ClearTextTest {
    private val dispatcher = StandardTestDispatcher()
    private val test = TestScope(dispatcher)
    private val tv = TextBox()
    private lateinit var vm: TellyscopeViewModel

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    private fun clear() = with(test) {
        vm = TellyscopeViewModel(tv, clock = { 0L })
        runCurrent()
        vm.clearText()
        runCurrent()
    }

    @Test fun aClearedBoxSaysNothing() {
        clear()
        assertEquals(1, tv.clears)
        assertNull(vm.notice.value)
    }

    @Test fun aBoxThatDidntClearSaysSo() {
        tv.answer = { false }
        clear()
        assertEquals(Copy.CLEAR_REFUSED, vm.notice.value)
    }

    @Test fun aLostLinkSaysTheRemoteLostTheTv() {
        tv.answer = { throw IOException("gone") }
        clear()
        assertEquals(Copy.REMOTE_LOST, vm.notice.value)
    }
}
