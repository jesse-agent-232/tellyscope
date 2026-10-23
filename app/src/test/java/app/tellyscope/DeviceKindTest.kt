// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.Device
import app.tellyscope.data.DeviceKind
import app.tellyscope.data.DeviceState
import app.tellyscope.data.ScanHit
import app.tellyscope.data.ScanKind
import app.tellyscope.data.deviceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceKindTest {
    private fun kind(manufacturer: String? = null, model: String? = null, maker: String? = null, hostname: String? = null) =
        deviceKind(Device("tv", ScanHit("tv", 5555, ScanKind.Classic, hostname, maker), DeviceState.Online, model, manufacturer, "11", null))

    @Test fun projectorMakers() {
        assertEquals(DeviceKind.Projector, kind(manufacturer = "XGIMI", model = "Horizon Pro"))
        assertEquals(DeviceKind.Projector, kind(manufacturer = "Anker Nebula", model = "Capsule 3"))
        assertEquals(DeviceKind.Projector, kind(maker = "Dangbei"))
    }

    @Test fun projectorInAName() {
        assertEquals(DeviceKind.Projector, kind(manufacturer = "Amlogic", hostname = "living-room-projector"))
    }

    @Test fun fireTv() {
        assertEquals(DeviceKind.FireTv, kind(manufacturer = "Amazon", model = "AFTMM"))
        assertEquals(DeviceKind.FireTv, kind(model = "AFTKA"))
        assertEquals(DeviceKind.FireTv, kind(maker = "Amazon"))
        assertEquals(DeviceKind.FireTv, kind(hostname = "bedroom-firetv"))
    }

    @Test fun sticksAndBoxes() {
        assertEquals(DeviceKind.Stick, kind(manufacturer = "Google", model = "Chromecast"))
        assertEquals(DeviceKind.Stick, kind(manufacturer = "Xiaomi", model = "Mi TV Stick"))
        assertEquals(DeviceKind.Stick, kind(manufacturer = "NVIDIA", model = "SHIELD Android TV"))
        assertEquals(DeviceKind.Stick, kind(maker = "Google", hostname = "chromecast"))
    }

    @Test fun everythingElseIsATv() {
        assertEquals(DeviceKind.Tv, kind(manufacturer = "Sony", model = "BRAVIA 4K VH2"))
        assertEquals(DeviceKind.Tv, kind(manufacturer = "TCL", model = "Smart TV Pro"))
        assertEquals(DeviceKind.Tv, kind())
    }

    /** "aft" only counts at the start of a model, so a name that merely contains it stays a TV. */
    @Test fun aftOnlyAsAPrefix() = assertEquals(DeviceKind.Tv, kind(manufacturer = "Philips", model = "Craft 55"))
}
