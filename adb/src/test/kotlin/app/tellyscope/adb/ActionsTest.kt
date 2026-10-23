// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ActionsTest {
    private val home = "cmd package resolve-activity" to ok("priority=0 preferredOrder=0\ncom.google.android.tvlauncher/.MainActivity\n")

    @Test fun disableReturnsEnableAsUndo() = runTest {
        val sh = FakeShell(mapOf(home, "pm disable-user" to ok("Package com.foo.ads new state: disabled-user\n")))
        assertEquals(ActionResult.Done(Action.ENABLE), Actions(sh).run(Action.DISABLE, "com.foo.ads"))
        assertTrue(sh.ran.contains("pm disable-user --user 0 com.foo.ads 2>&1"))
    }

    @Test fun stopUndoIsOpenAgain() = runTest {
        val sh = FakeShell(mapOf(home))
        assertEquals(ActionResult.Done(Action.OPEN), Actions(sh).run(Action.STOP, "com.google.android.youtube.tv"))
    }

    @Test fun refusesCurrentLauncherWhateverItsName() = runTest {
        val sh = FakeShell(mapOf("cmd package resolve-activity" to ok("com.example.customhome/.Home\n")))
        assertEquals(ActionResult.Refused(ProtectedReason.HOME_SCREEN), Actions(sh).run(Action.DISABLE, "com.example.customhome"))
        assertTrue(sh.ran.none { it.startsWith("pm ") })
    }

    @Test fun noDefaultLauncherProtectsEveryLauncher() = runTest {
        val sh = FakeShell(mapOf(
            "cmd package resolve-activity" to ok("priority=0\nandroid/com.android.internal.app.ResolverActivity\n"),
            "cmd package query-activities" to ok("com.google.android.tvlauncher/.MainActivity\ncom.spocky.projengmenu/.Main\n"),
        ))
        assertEquals(setOf("com.google.android.tvlauncher", "com.spocky.projengmenu"), Actions(sh).homePackages())
        assertEquals(ActionResult.Refused(ProtectedReason.HOME_SCREEN), Actions(sh).run(Action.DISABLE, "com.spocky.projengmenu"))
    }

    @Test fun unknownHomeRefusesStopAndDisable() = runTest {
        // Android 6 and older have no `cmd`: both launcher lookups come back empty.
        val sh = FakeShell(mapOf("cmd package" to ShellResult("/system/bin/sh: cmd: not found\n", 127)))
        val a = Actions(sh)
        assertEquals(ActionResult.Refused(ProtectedReason.UNKNOWN_HOME), a.run(Action.DISABLE, "com.example.tvlauncher"))
        assertEquals(ActionResult.Refused(ProtectedReason.UNKNOWN_HOME), a.run(Action.STOP, "com.foo.ads"))
        assertTrue(sh.ran.none { it.startsWith("pm ") || it.startsWith("am ") })
    }

    @Test fun launcherNamesAreProtectedEvenWhenNotDefault() = runTest {
        assertEquals(ActionResult.Refused(ProtectedReason.HOME_SCREEN), Actions(FakeShell(mapOf(home))).run(Action.DISABLE, "com.example.tvlauncher"))
    }

    @Test fun refusesProtectedPackages() = runTest {
        val a = Actions(FakeShell(mapOf(home)))
        assertEquals(ActionResult.Refused(ProtectedReason.PLAY_SERVICES), a.run(Action.DISABLE, "com.google.android.gms"))
        assertEquals(ActionResult.Refused(ProtectedReason.KEYBOARD), a.run(Action.STOP, "com.google.android.leanback.ime"))
        assertEquals(ActionResult.Refused(ProtectedReason.TV_INPUTS_REMOTE), a.run(Action.DISABLE, "com.xgimi.hdmiinput"))
    }

    @Test fun enablingProtectedIsAllowed() = runTest {
        val sh = FakeShell(mapOf(home, "pm enable" to ok("Package com.android.vending new state: enabled\n")))
        assertEquals(ActionResult.Done(Action.DISABLE), Actions(sh).run(Action.ENABLE, "com.android.vending"))
    }

    @Test fun pmErrorIsFailure() = runTest {
        val sh = FakeShell(mapOf(home, "pm disable-user" to ok("Error: java.lang.SecurityException: Shell cannot change component state\n")))
        assertTrue(Actions(sh).run(Action.DISABLE, "com.foo") is ActionResult.Failed)
    }

    @Test fun uninstallsOnlyWhatTheUserInstalled() = runTest {
        val sh = FakeShell(mapOf(
            home,
            "pm list packages -3" to ok("package:com.netflix.ninja\npackage:com.amazon.amazonvideo.livingroom\n"),
            "pm uninstall" to ok("Success\n"),
        ))
        assertEquals(ActionResult.Done(null), Actions(sh).run(Action.UNINSTALL, "com.netflix.ninja"))
        assertTrue(sh.ran.contains("pm uninstall com.netflix.ninja 2>&1"))
    }

    @Test fun refusesToUninstallWhatCameWithTheTv() = runTest {
        val sh = FakeShell(mapOf(home, "pm list packages -3" to ok("package:com.netflix.ninja\n")))
        assertEquals(ActionResult.Refused(ProtectedReason.CAME_WITH_TV), Actions(sh).run(Action.UNINSTALL, "com.google.android.youtube.tv"))
        assertTrue(sh.ran.none { it.startsWith("pm uninstall") })
    }

    @Test fun neverUninstallsForOneUser() = runTest {
        val sh = FakeShell(mapOf(home, "pm list packages -3" to ok("package:com.netflix.ninja\n"), "pm uninstall" to ok("Success\n")))
        Actions(sh).run(Action.UNINSTALL, "com.netflix.ninja")
        assertTrue(sh.ran.none { "--user" in it && "uninstall" in it })
    }

    @Test fun uninstallRefusesProtectedAndUnknownHome() = runTest {
        val user = "pm list packages -3" to ok("package:com.google.android.gms\npackage:com.foo.ads\n")
        assertEquals(ActionResult.Refused(ProtectedReason.PLAY_SERVICES), Actions(FakeShell(mapOf(home, user))).run(Action.UNINSTALL, "com.google.android.gms"))
        val noHome = FakeShell(mapOf(user, "cmd package" to ShellResult("/system/bin/sh: cmd: not found\n", 127)))
        assertEquals(ActionResult.Refused(ProtectedReason.UNKNOWN_HOME), Actions(noHome).run(Action.UNINSTALL, "com.foo.ads"))
        assertTrue(noHome.ran.none { it.startsWith("pm uninstall") })
    }

    @Test fun uninstallFailureIsReported() = runTest {
        val sh = FakeShell(mapOf(home, "pm list packages -3" to ok("package:com.foo\n"), "pm uninstall" to ok("Failure [DELETE_FAILED_INTERNAL_ERROR]\n")))
        assertTrue(Actions(sh).run(Action.UNINSTALL, "com.foo") is ActionResult.Failed)
    }

    @Test fun openFallsBackToPhoneLauncher() = runTest {
        val sh = FakeShell(mapOf(
            "monkey -p com.foo -c android.intent.category.LEANBACK_LAUNCHER" to ok("** No activities found to run, monkey aborted.\n"),
            "monkey -p com.foo -c android.intent.category.LAUNCHER" to ok("Events injected: 1\n"),
        ))
        assertEquals(ActionResult.Done(null), Actions(sh).run(Action.OPEN, "com.foo"))
        assertEquals(2, sh.ran.count { it.startsWith("monkey") })
    }

    @Test fun rejectsShellInjection() = runTest {
        val sh = FakeShell()
        assertFailsWith<IllegalArgumentException> { Actions(sh).run(Action.STOP, "com.foo; reboot") }
        assertFailsWith<IllegalArgumentException> { Actions(sh).run(Action.STOP, "\$(reboot)") }
        assertTrue(sh.ran.isEmpty())
    }
}
