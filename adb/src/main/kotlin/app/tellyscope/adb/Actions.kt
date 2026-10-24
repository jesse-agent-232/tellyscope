// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

/**
 * The only changes Tellyscope makes. Undo for [STOP] is [OPEN] ("open again"); a real undo isn't
 * possible. [UNINSTALL] has no undo at all, so it only ever runs on apps the user installed.
 */
enum class Action { STOP, DISABLE, ENABLE, OPEN, UNINSTALL }

sealed interface ActionResult {
    data class Done(val undo: Action?) : ActionResult
    data class Refused(val reason: ProtectedReason) : ActionResult
    data class Failed(val output: String) : ActionResult
}

/** What [Actions.makeHome] did. [from] and [to] are the old and new home activities, for Undo. */
sealed interface HomeResult {
    data class Switched(val from: String, val to: String) : HomeResult
    data class Refused(val reason: ProtectedReason) : HomeResult
    data class Failed(val output: String) : HomeResult
}

/** The launcher Home opens now, and the other launchers that could take its place. */
data class HomeOptions(val current: String, val others: Set<String>)

class Actions(private val shell: AdbShell) {

    suspend fun run(action: Action, pkg: String): ActionResult {
        require(PACKAGE.matches(pkg)) { "not a package name: $pkg" } // it goes into a shell line
        if (action != Action.ENABLE && action != Action.OPEN) {
            // No launcher answer (no `cmd` before Android 7, or an error): we can't tell what's safe.
            val home = homePackages().ifEmpty { return ActionResult.Refused(ProtectedReason.UNKNOWN_HOME) }
            Protected.reason(pkg, home)?.let { return ActionResult.Refused(it) }
        }
        return when (action) {
            Action.STOP -> exec("am force-stop $pkg", Action.OPEN)
            Action.DISABLE -> exec("pm disable-user --user 0 $pkg", Action.ENABLE) { "disabled-user" in it }
            Action.ENABLE -> exec("pm enable --user 0 $pkg", Action.DISABLE) { "enabled" in it }
            Action.OPEN -> open(pkg)
            Action.UNINSTALL -> uninstall(pkg)
        }
    }

    /**
     * Only for packages `pm list packages -3` lists, i.e. installed by the user. Plain
     * `pm uninstall`, never `--user 0`: that form removes system apps for a user, and Tellyscope
     * never does that.
     */
    private suspend fun uninstall(pkg: String): ActionResult {
        val userApps = Parsers.packages(shell.shell(Commands.USER_PACKAGES).output).map { it.name }
        if (pkg !in userApps) return ActionResult.Refused(ProtectedReason.CAME_WITH_TV)
        return exec("pm uninstall $pkg", undo = null) { "Success" in it }
    }

    /**
     * Launchers to protect: the default one, or every installed launcher when there's no default
     * (then resolve-activity answers with the system chooser, package `android`).
     */
    suspend fun homePackages(): Set<String> {
        val current = packagesIn(shell.shell(Commands.HOME_ACTIVITY).output) - "android"
        return current.ifEmpty { packagesIn(shell.shell(Commands.HOME_ACTIVITIES).output) - "android" }
    }

    /** Null when the TV has no default home (it asks each time) or didn't answer. */
    suspend fun homeOptions(): HomeOptions? {
        val current = currentHome()?.substringBefore('/') ?: return null
        return HomeOptions(current, launchers().keys - current)
    }

    /**
     * Makes [pkg] the home screen in place of the current one, then disables the old launcher so
     * the TV can't switch back by itself. Home is checked to open [pkg] before anything is
     * disabled. Android skips the default when one launcher has a higher Home priority than the
     * rest, and Android TV Home has one, so if that check fails the old launcher is turned off
     * and Home checked again. If Home still doesn't open [pkg], the old one goes back on and Home.
     */
    suspend fun makeHome(pkg: String): HomeResult {
        require(PACKAGE.matches(pkg)) { "not a package name: $pkg" }
        val from = currentHome() ?: return HomeResult.Refused(ProtectedReason.UNKNOWN_HOME)
        val old = from.substringBefore('/')
        if (old == pkg) return HomeResult.Failed("it's already the home screen")
        val to = launchers()[pkg] ?: return HomeResult.Failed("it has no home screen")
        // The old launcher is protected as the home screen and by name; once it isn't Home, that
        // guard has done its job. Any other reason still stands.
        Protected.reason(old)?.takeIf { it != ProtectedReason.HOME_SCREEN }?.let { return HomeResult.Refused(it) }
        val checked = setHome(to)
        val off = exec("pm disable-user --user 0 $old", undo = null) { "disabled-user" in it }
        if (off is ActionResult.Failed) {
            setHome(from)
            return HomeResult.Failed(off.output)
        }
        if (checked || setHome(to)) return HomeResult.Switched(from, to)
        val on = exec("pm enable --user 0 $old", undo = null) { "enabled" in it }
        setHome(from)
        return HomeResult.Failed(
            if (on is ActionResult.Failed) "Home didn't open it, and the old home screen didn't turn back on: ${on.output}"
            else "the TV kept its own home screen, so nothing was changed",
        )
    }

    /**
     * Makes [pkg] Home again after another launcher took over: turns it on if it's off, then sets
     * [activity], the one the switch away from it saved, or else its own home activity. The
     * launcher that was Home stays on, so it can be made Home again or uninstalled.
     */
    suspend fun homeBack(pkg: String, activity: String? = null): HomeResult {
        require(PACKAGE.matches(pkg)) { "not a package name: $pkg" }
        require(activity == null || (ACTIVITY.matches(activity) && activity.substringBefore('/') == pkg)) { "not $pkg's activity: $activity" }
        val from = currentHome() ?: return HomeResult.Refused(ProtectedReason.UNKNOWN_HOME)
        if (from.substringBefore('/') == pkg) return HomeResult.Failed("it's already the home screen")
        val on = exec("pm enable --user 0 $pkg", undo = null) { "enabled" in it }
        if (on is ActionResult.Failed) return HomeResult.Failed(on.output)
        val to = activity ?: launchers()[pkg] ?: return HomeResult.Failed("it has no home screen")
        if (!setHome(to)) {
            setHome(from)
            return HomeResult.Failed("the TV kept the home screen it has")
        }
        return HomeResult.Switched(from, to)
    }

    /** Makes [activity] (`package/class`) the default home, then checks that Home opens it. */
    suspend fun setHome(activity: String): Boolean {
        require(ACTIVITY.matches(activity)) { "not an activity: $activity" }
        shell.shell("${Commands.SET_HOME} '$activity' 2>&1") // a nested class has a $
        return currentHome()?.substringBefore('/') == activity.substringBefore('/')
    }

    /** The activity Home opens, or null when Android would ask (its chooser lives in `android`). */
    private suspend fun currentHome(): String? =
        activitiesIn(shell.shell(Commands.HOME_ACTIVITY).output).singleOrNull()?.takeUnless { it.startsWith("android/") }

    /**
     * Every enabled launcher, package to its home activity. Apps protected for another reason
     * aren't launchers, though they may answer Home: TV Settings has a fallback home screen.
     */
    private suspend fun launchers(): Map<String, String> =
        activitiesIn(shell.shell(Commands.HOME_ACTIVITIES).output).filterNot { it.startsWith("android/") }
            .associateBy { it.substringBefore('/') }
            .filterKeys { Protected.reason(it).let { r -> r == null || r == ProtectedReason.HOME_SCREEN } }

    private fun packagesIn(brief: String): Set<String> = activitiesIn(brief).map { it.substringBefore('/') }.toSet()

    private fun activitiesIn(brief: String): List<String> =
        brief.lineSequence().map(String::trim).filter { '/' in it && ' ' !in it }.distinct().toList()

    /** TV apps register under LEANBACK_LAUNCHER only; phone-style apps under LAUNCHER. Try both. */
    private suspend fun open(pkg: String): ActionResult {
        var last = ""
        for (category in listOf("android.intent.category.LEANBACK_LAUNCHER", "android.intent.category.LAUNCHER")) {
            val r = shell.shell("monkey -p $pkg -c $category 1 2>&1")
            if (r.exitCode == 0 && "No activities found" !in r.output && "aborted" !in r.output) {
                return ActionResult.Done(undo = null)
            }
            last = r.output
        }
        return ActionResult.Failed(last.trim())
    }

    private suspend fun exec(command: String, undo: Action?, ok: (String) -> Boolean = { true }): ActionResult {
        val r = shell.shell("$command 2>&1")
        return if (r.exitCode == 0 && ok(r.output)) ActionResult.Done(undo) else ActionResult.Failed(r.output.trim())
    }

    internal companion object {
        val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+|android")
        val ACTIVITY = Regex("(${PACKAGE.pattern})/[A-Za-z0-9_.$]+")
    }
}
