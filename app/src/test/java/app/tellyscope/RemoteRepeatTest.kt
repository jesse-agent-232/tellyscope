// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.adb.RemoteKey
import app.tellyscope.ui.repeatWhileHeld
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteRepeatTest {
    @Test fun heldKeyStopsWhenTheTvStopsTakingIt() = runTest {
        var presses = 0
        // Still held: only the TV's third "no" ends it.
        repeatWhileHeld(RemoteKey.UP, held = { true }) { presses++ < 2 }
        assertEquals(3, presses)
    }

    @Test fun noTvMeansOnePress() = runTest {
        var presses = 0
        repeatWhileHeld(RemoteKey.UP, held = { true }) { presses++; false }
        assertEquals(1, presses)
    }

    @Test fun releaseEndsTheRepeat() = runTest {
        var presses = 0
        var held = true
        repeatWhileHeld(RemoteKey.VOLUME_UP, held = { held }) { presses++; if (presses == 5) held = false; true }
        assertEquals(5, presses)
    }

    @Test fun okNeverRepeats() = runTest {
        var presses = 0
        repeatWhileHeld(RemoteKey.OK, held = { true }) { presses++; true }
        assertEquals(1, presses)
    }
}
