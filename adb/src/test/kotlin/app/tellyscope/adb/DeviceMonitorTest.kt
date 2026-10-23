// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeviceMonitorTest {
    private val sep = "\n@@tellyscope@@\n"

    /** Each FAST call advances the CPU counters by busy +25 / total +100. */
    private fun device(): AdbShell {
        var tick = 0L
        return AdbShell { cmd ->
            if (cmd.startsWith("cat /proc/stat")) {
                tick++
                ok("cpu  ${tick * 25} 0 0 ${tick * 75} 0 0 0 0\n${sep}MemTotal: 2000 kB\nMemAvailable: 500 kB\n")
            } else {
                ok("[ro.product.model]: [XK03H]\n[ro.build.version.sdk]: [30]\n$sep" +
                    "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/x 16000 9800 6200 62% /data\n$sep" +
                    "300.5 100.0\n$sep" + "cpu-thermal=52000\n")
            }
        }
    }

    @Test fun firstReadingHasEverything() = runTest {
        val s = DeviceMonitor(device()).stats().first()
        assertEquals(25.0, s.cpuPercent)
        assertEquals(Parsers.Memory(2000, 500), s.memory)
        assertEquals(Parsers.Storage(16000, 9800), s.storage)
        assertEquals(300L, s.uptimeSeconds)
        assertEquals(52.0, s.temperatureC)
        assertEquals("XK03H", s.model)
        assertEquals(30, s.sdk)
    }

    @Test fun keepsEmitting() = runTest {
        assertEquals(3, DeviceMonitor(device()).stats().take(3).toList().size)
    }

    @Test fun silentDeviceThrowsIo() = runTest {
        val hung = AdbShell { kotlinx.coroutines.awaitCancellation() }
        assertFailsWith<IOException> { DeviceMonitor(hung).stats().first() }
    }

    @Test fun processesCarryTheirPackageAndScreenState() = runTest {
        val tv = AdbShell { cmd ->
            when {
                cmd.startsWith("cat /proc/stat") -> ok("cpu  25 0 0 75 0 0 0 0\n${sep}MemTotal: 2000 kB\nMemAvailable: 500 kB\n")
                cmd.startsWith("top") -> ok(
                    "PID USER RES %CPU NAME\n2201 u0_a87 312M 3.5 com.google.android.youtube.tv\n512 system 120M 9.0 system_server\n" +
                        "$sep  mWakefulness=Asleep\n",
                )
                else -> ok("$sep$sep$sep${sep}package:com.google.android.youtube.tv uid:10087\n")
            }
        }
        val s = DeviceMonitor(tv).stats().first()
        assertEquals(
            listOf(
                AppProcess(2201, "com.google.android.youtube.tv", "com.google.android.youtube.tv", 3.5, 312 * 1024),
                AppProcess(512, "system_server", null, 9.0, 120 * 1024),
            ),
            s.processes,
        )
        assertEquals(false, s.screenOn)
        assertEquals(mapOf(10087 to listOf("com.google.android.youtube.tv")), s.packages)
    }
}
