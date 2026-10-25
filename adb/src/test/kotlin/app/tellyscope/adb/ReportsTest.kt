// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.test.runTest
import java.net.URI
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportsTest {
    private val app = AppFacts("0.8.0", "Pixel 7, Android 14")
    private val tv = TvFacts(
        manufacturer = "XGIMI", model = "XK03H", device = "galileo", androidVersion = "11", sdk = 30,
        build = "RTT1.230922.001", stockPackages = listOf("com.google.android.tvlauncher", "com.xgimi.filemanager"),
        userApps = 7, identifiers = setOf("XK03HSN12345678"),
    )

    /** The query of [link] as the form sees it. */
    private fun query(link: String): Map<String, String> = URI(link).rawQuery.split('&').associate {
        it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
    }

    @Test fun problemGoesToOurFormWithEveryField() {
        val r = Reports.problem(app, tv, Reports.Connection.PORT_5555, Reports.Outcome.MISBEHAVED, "Remote keys lag", log = "line 1")
        assertTrue(r.link.startsWith("https://github.com/${Reports.OURS}/issues/new?"), r.link)
        val q = query(r.link)
        assertEquals("device_report.yml", q["template"])
        assertEquals("[Device] XGIMI XK03H (galileo)", q["title"])
        assertEquals("XGIMI XK03H (galileo)", q["model"])
        assertEquals("Android 11 (API 30), build RTT1.230922.001", q["android-version"])
        assertEquals("Port 5555 (classic network debugging)", q["connection"])
        assertEquals("0.8.0", q["app-version"])
        assertEquals("Connected, but something misbehaved", q["outcome"])
        assertEquals("Remote keys lag\n\nTellyscope 0.8.0 on Pixel 7, Android 14\n\nLog:\n```\nline 1\n```", q["details"])
        assertTrue(r.fits)
        assertNull(r.clipboard)
    }

    @Test fun problemBeforeAnyTvConnected() {
        val q = query(Reports.problem(app, null, Reports.Connection.COULD_NOT_CONNECT, Reports.Outcome.COULD_NOT_CONNECT, "", null).link)
        assertEquals("Unknown TV", q["model"])
        assertEquals("Unknown", q["android-version"])
        assertTrue(q.getValue("details").startsWith("(nothing written)"))
    }

    @Test fun newTvGoesToCleanyourtvsMyTvForm() {
        val r = Reports.newTv(app, tv, Reports.Stage.COULD_NOT_CONNECT, "Stuck at Allow")
        assertTrue(r.link.startsWith("https://github.com/Gathin23/cleanyourtv/issues/new?"), r.link)
        val q = query(r.link)
        assertEquals(setOf("template", "title", "model", "android", "stage", "details"), q.keys)
        assertEquals("tv-list.yml", q["template"])
        assertEquals("[TV] XGIMI XK03H (Android 11)", q["title"])
        assertEquals("Android 11 (API 30), build RTT1.230922.001", q["android"])
        assertEquals("Could not connect over ADB", q["stage"])
        val details = q.getValue("details")
        assertTrue(details.startsWith("Stuck at Allow\n\nApps the TV came with (2):\ncom.google.android.tvlauncher\ncom.xgimi.filemanager\n"), details)
        assertTrue("User-installed apps: 7 (not listed)" in details)
    }

    @Test fun spacesAreEncodedAsPercent20() {
        val link = Reports.newTv(app, tv, Reports.Stage.HERE_IS_MY_LIST, "a b").link
        assertFalse('+' in link.substringAfter('?').replace("%2B", ""), link)
        assertFalse(' ' in link)
    }

    @Test fun longReportLeavesDetailsForTheClipboard() {
        val big = tv.copy(stockPackages = (1..400).map { "com.vendor.app$it" })
        val r = Reports.newTv(app, big, Reports.Stage.HERE_IS_MY_LIST, "")
        assertFalse(r.fits)
        assertTrue(r.link.length <= Report.MAX_URL, "${r.link.length}")
        val q = query(r.link)
        assertNull(q["details"])
        assertEquals("XGIMI XK03H (galileo)", q["model"])
        assertEquals(r.fields["details"], r.clipboard)
        assertTrue("com.vendor.app400" in r.clipboard!!)
    }

    @Test fun previewShowsEveryFieldUnderItsLabel() {
        val text = Reports.newTv(app, tv, Reports.Stage.BROKE_AFTER, "").text
        assertTrue(text.startsWith("[TV] XGIMI XK03H (Android 11)\n\n### TV model\n\nXGIMI XK03H (galileo)\n"), text)
        assertTrue("### How far did you get?\n\nSomething broke after the debloat\n" in text)
    }

    @Test fun reportTextIsRedacted() {
        val r = Reports.problem(
            app, tv, Reports.Connection.WIRELESS_DEBUGGING, Reports.Outcome.MISBEHAVED,
            details = "Paired with code 482913 at 192.0.2.10:37155, serial XK03HSN12345678",
            log = "10-25 12:30:45.123 connect adb-XK03HSN12345678-AbCdEf._adb-tls-connect._tcp",
        )
        val details = r.fields.getValue("details")
        assertFalse("482913" in details || "192.0.2" in details || "XK03HSN" in details, details)
        assertTrue("10-25 12:30:45.123" in details, details) // timestamps aren't addresses
    }

    @Test fun redactsAddresses() {
        assertEquals("tv at <ip>:5555", Redact.text("tv at 192.0.2.10:5555"))
        assertEquals("Can't reach <ip>.", Redact.text("Can't reach 192.0.2.10."))
        assertEquals("gw <ip> and <ip>", Redact.text("gw fe80::1c2b:3aff:fe4d%wlan0 and 2001:db8:0:0:0:0:2:1"))
        assertEquals("wlan0 <mac>", Redact.text("wlan0 a4:5e:60:c2:11:9f"))
        assertEquals("signed in as <email>", Redact.text("signed in as alex.smith@gmail.com"))
    }

    @Test fun redactsKeysAndFingerprints() {
        val key = "QAAAA" + "x".repeat(600) + "= alex@laptop"
        assertEquals("key <adb key>", Redact.text("key $key"))
        assertEquals("fp <fingerprint>", Redact.text("fp 1A:2B:3C:4D:5E:6F:70:81:92:A3:B4:C5:D6:E7:F8:09"))
        assertEquals("<key>", Redact.text("-----BEGIN PRIVATE KEY-----\nMIIE\nabc\n-----END PRIVATE KEY-----"))
        assertEquals("sha <fingerprint>", Redact.text("sha " + "ab12".repeat(16)))
    }

    @Test fun redactsSerialsAndCodes() {
        assertEquals("serialno: <serial>", Redact.text("serialno: 0123ABCDEF"))
        assertEquals("pairing code <code>", Redact.text("pairing code 123456"))
        assertEquals("SSID: <wifi> BSSID: <mac>", Redact.text("SSID: \"Smith Family 5G\" BSSID: a4:5e:60:c2:11:9f"))
        assertEquals("found adb-<serial>._adb-tls-pairing._tcp", Redact.text("found adb-0123ABCDEF-Xy7kQz._adb-tls-pairing._tcp"))
        assertEquals("my <serial> box", Redact.text("my Living Room TV box", setOf("Living Room TV")))
    }

    @Test fun leavesWhatAReportNeeds() {
        val s = "com.google.android.tvlauncher disabled on Android 11 build RTT1.230922.001 at 12:30:45, keycode 82, " +
            "versionCode=310100 minSdk=28, java.io.NotSerializableException at AsyncTask\$SerialExecutor, " +
            "_adb-tls-connect._tcp, version 1.2.3.4.5"
        assertEquals(s, Redact.text(s))
    }

    @Test fun readsTheTvAndKeepsSerialsOut() = runTest {
        val sh = FakeShell(
            mapOf(
                "getprop" to ok(
                    "[ro.product.manufacturer]: [XGIMI]\n[ro.product.model]: [XK03H]\n[ro.product.device]: [galileo]\n" +
                        "[ro.build.version.release]: [11]\n[ro.build.version.sdk]: [30]\n[ro.build.display.id]: [RTT1.230922.001]\n" +
                        "[ro.serialno]: [XK03HSN12345678]\n[persist.sys.device_name]: [XGIMI XK03H]\n",
                ),
                Commands.SYSTEM_PACKAGES to ok("package:com.xgimi.filemanager\npackage:android\n"),
                Commands.USER_PACKAGES to ok("package:org.videolan.vlc\npackage:com.spocky.projengmenu\n"),
            ),
        )
        val facts = TvFacts.read(sh)
        assertEquals(tv.copy(stockPackages = listOf("android", "com.xgimi.filemanager"), userApps = 2), facts)
        assertFalse(facts.toString().let { "vlc" in it || "projengmenu" in it })
    }
}
