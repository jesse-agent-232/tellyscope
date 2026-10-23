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

    private fun packagesIn(brief: String): Set<String> =
        brief.lineSequence().map(String::trim).filter { '/' in it && ' ' !in it }.map { it.substringBefore('/') }.toSet()

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
    }
}
