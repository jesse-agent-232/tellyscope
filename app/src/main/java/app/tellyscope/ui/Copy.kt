// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.ui

import app.tellyscope.adb.InstallKind
import app.tellyscope.adb.InstallRefusal
import app.tellyscope.adb.TvFolder
import app.tellyscope.data.Action
import app.tellyscope.data.DeviceState
import app.tellyscope.data.Snapshot

/**
 * Every user-facing sentence about actions and time lives here so wording is
 * reviewed in one place: every button says what it does before it runs.
 */
object Copy {
    fun button(action: Action) = when (action) {
        Action.Stop -> "Stop"
        Action.Disable -> "Disable"
        Action.Enable -> "Turn on"
        Action.Open -> "Open"
    }

    fun confirm(action: Action) = when (action) {
        Action.Stop -> "Yes, stop it"
        Action.Disable -> "Yes, disable it"
        Action.Enable -> "Yes, turn it on"
        Action.Open -> "Yes, open it"
    }

    /**
     * Names the package next to the label: the label comes from the app itself, and any app can
     * call itself "Settings". The package id is what actually gets changed.
     */
    fun explain(action: Action, app: String, pkg: String) = "$app ($pkg)".let { who ->
        when (action) {
            Action.Stop -> "Stops $who until you open it again."
            Action.Disable -> "Hides $who and stops it running. Nothing is deleted; you can turn it back on here."
            Action.Enable -> "Turns $who back on."
            Action.Open -> "Opens $who on the TV."
        }
    }

    fun done(action: Action, app: String) = when (action) {
        Action.Stop -> "Stopped $app"
        Action.Disable -> "Disabled $app"
        Action.Enable -> "Turned on $app"
        Action.Open -> "Opened $app"
    }

    /** Force-stop has no true undo; the button says what it actually does. */
    fun undo(undo: Action) = if (undo == Action.Open) "Open again" else "Undo"

    fun ago(at: Long, now: Long): String {
        val s = ((now - at) / 1000).coerceAtLeast(0)
        return when {
            s < 10 -> "just now"
            s < 60 -> "$s s ago"
            s < 3600 -> "${s / 60} min ago"
            s < 86_400 -> "${s / 3600} h ago"
            else -> "${s / 86_400} d ago"
        }
    }

    fun uptime(s: Long): String {
        val d = s / 86_400
        val h = (s % 86_400) / 3600
        val m = (s % 3600) / 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    fun mb(v: Int) = if (v >= 1024) "%.1f GB".format(v / 1024.0) else "$v MB"

    fun disabledSuggested(done: Int, of: Int) = when {
        done == of -> "Disabled $done ${if (done == 1) "app" else "apps"}. Undo any of them from History."
        done == 0 -> "The TV didn't disable any of them."
        else -> "Disabled $done of $of apps; the TV refused the rest. Undo any of them from History."
    }

    const val ANDROID_SYSTEM = "Android system"

    /** The word next to the status dot, so the state never rests on colour alone. */
    fun state(s: DeviceState) = when (s) {
        DeviceState.Online -> "Connected"
        DeviceState.Offline -> "Offline"
        DeviceState.NotAllowed -> "Not allowed"
        DeviceState.Waiting -> "Connecting"
        DeviceState.NeedsPairing -> "Needs the pairing code"
        DeviceState.Found -> "Not connected"
    }

    /** Under 10 GB a whole number hides too much: a 1.5 GB TV isn't a 2 GB one. */
    fun gbOf(used: Double, total: Double) =
        if (total < 10) "%.1f of %.1f GB".format(used, total) else "%.1f of %.0f GB".format(used, total)

    /** An offline TV that has never sent stats; a refusing TV is on, it just hasn't allowed us. */
    fun noStats(s: DeviceState) =
        if (s == DeviceState.NotAllowed) "Stats show up once the TV allows this phone."
        else "No stats yet. They show up once the TV is back on."

    const val WRONG_CODE = "That code didn't work. Check the 6 digits on the TV and try again."
    const val UNREACHABLE = "The TV didn't answer. Check it's on and on the same Wi-Fi as this phone, then try again."

    data class Health(val ok: Boolean, val text: String)

    /**
     * The one line at the top of a TV's page: the most pressing problem, or that all is well.
     * [heaviest] are app names by memory use, largest first; there is no per-app storage size yet.
     */
    fun health(s: Snapshot, heaviest: List<String>): Health {
        val storage = s.storageUsedGb?.let { u -> s.storageTotalGb?.takeIf { it > 0 }?.let { u / it } }
        val ram = if (s.ramTotalMb > 0) s.ramUsedMb.toDouble() / s.ramTotalMb else null
        val most = heaviest.take(2).takeIf { it.isNotEmpty() }?.let { ": ${it.joinToString(" and ")} use the most" }.orEmpty()
        return when {
            storage != null && storage >= FULL -> Health(false, "Storage almost full")
            ram != null && ram >= FULL -> Health(false, "Memory almost full$most")
            (s.tempC ?: 0) >= HOT_C -> Health(false, "Running hot")
            else -> Health(true, "Running fine")
        }
    }

    const val FULL = 0.9
    private const val HOT_C = 80

    fun bytes(v: Long): String = when {
        v >= 1L shl 30 -> "%.1f GB".format(v / (1L shl 30).toDouble())
        v >= 1L shl 20 -> "%.1f MB".format(v / (1L shl 20).toDouble())
        v >= 1L shl 10 -> "${v shr 10} KB"
        else -> "$v bytes"
    }

    fun folder(f: TvFolder) = when (f) {
        TvFolder.DOWNLOAD -> "Download"
        TvFolder.MOVIES -> "Movies"
    }

    // Sideload

    fun installButton(kind: InstallKind) = when (kind) {
        InstallKind.NEW -> "Install"
        InstallKind.UPDATE -> "Update"
        InstallKind.REINSTALL -> "Reinstall"
        InstallKind.DOWNGRADE -> "Install older version"
    }

    fun installKind(kind: InstallKind, onTv: String?, picked: String?) = when (kind) {
        InstallKind.NEW -> "Not on this TV yet. This installs it."
        InstallKind.UPDATE -> "Updates the TV's copy" + versions(onTv, picked) + ". Its settings stay."
        InstallKind.REINSTALL -> "The TV already has this version" + (onTv?.let { " ($it)" } ?: "") + ". This installs it again over itself."
        InstallKind.DOWNGRADE -> "This is older than the one on the TV" +
            (if (onTv != null && picked != null) " ($picked here, $onTv on the TV)" else "") +
            ". Android normally refuses that, and the app's saved data may not work with the older version."
    }

    private fun versions(onTv: String?, picked: String?) =
        if (onTv != null && picked != null) " from $onTv to $picked" else ""

    const val ALLOW_DOWNGRADE = "Install the older version anyway"
    const val INSTALL_KEEP_OPEN = "Keep Tellyscope open until it's done."

    fun installed(kind: InstallKind, app: String) = when (kind) {
        InstallKind.UPDATE -> "Updated $app."
        InstallKind.REINSTALL -> "Reinstalled $app."
        InstallKind.NEW, InstallKind.DOWNGRADE -> "Installed $app."
    }

    fun installRefused(reason: InstallRefusal, app: String) = when (reason) {
        InstallRefusal.SIGNATURE ->
            "The $app on the TV comes from a different developer, so Android won't replace it. Tellyscope never uninstalls, so if you're sure, uninstall it on the TV first."
        InstallRefusal.DOWNGRADE -> "The TV has a newer $app and won't go back to an older one."
        InstallRefusal.NO_SPACE -> "The TV is out of space. Disable or uninstall something there, then try again."
        InstallRefusal.TV_SAID_NO ->
            "The TV blocked the install. Look in the TV's security settings for installing apps from unknown sources, or for app verification, then try again."
        InstallRefusal.TV_TOO_OLD -> "$app needs a newer version of Android than this TV has."
        InstallRefusal.WRONG_CPU ->
            "This $app is built for a different kind of processor. Look for a version made for this TV, often marked armeabi-v7a or arm64-v8a."
        InstallRefusal.APP_TOO_OLD -> "$app was made for a very old version of Android, and this TV won't install it."
        InstallRefusal.MISSING_FEATURE -> "$app needs something this TV doesn't have, like a touchscreen or a camera."
        InstallRefusal.MISSING_PARTS ->
            "$app comes in several parts and one is missing. Pick all of its .apk files together, or its .apks bundle."
        InstallRefusal.TEST_ONLY -> "This is a developer test build of $app, which the TV won't install."
        InstallRefusal.CONFLICT -> "Another app on the TV already uses something $app needs, so the TV refused it."
        InstallRefusal.BROKEN -> "This file isn't a working app. It may be damaged or only half downloaded; try getting it again."
        InstallRefusal.UNKNOWN -> "The TV didn't install $app."
    }

    const val NOT_AN_APP = "That file isn't an Android app. Pick an .apk, several split .apk files, or an .apks or .xapk bundle."
    const val SIDELOAD_LOST = "Lost the TV partway through. Check it's still on and connected, then try again."

    // Files

    fun sent(name: String, savedAs: String, f: TvFolder) =
        if (savedAs == name) "Sent $name to ${folder(f)}" else "Sent to ${folder(f)} as $savedAs, since $name was already there"

    fun saved(name: String) = "Saved $name to your phone"
    fun sendFailed(name: String) = "Couldn't send $name. Check the TV is still connected and has space."
    fun saveFailed(name: String) = "Couldn't save $name. Check the TV is still connected and the phone has space."

    const val CANCELLED_SEND = "Cancelled. Nothing was left on the TV."
    const val CANCELLED_SAVE = "Cancelled. Nothing was saved."
    const val CANT_READ = "Couldn't read that file on your phone."
    const val FILES_LOST = "Couldn't read this folder. Check the TV is still connected."

    // Remote

    const val KEY_REFUSED = "The TV didn't take that key."
    const val TYPE_REFUSED = "The TV didn't take the text. Select a text box on the TV first, then send again."
    const val REMOTE_LOST = "Lost the TV. Check it's still on and connected."
    const val REBOOT_REFUSED = "The TV said no to restarting. Use its own remote or power button instead."
    const val REBOOT_IGNORED = "The TV didn't restart. Use its own remote or power button instead."
    const val REMOTE_OFFLINE = "The TV is offline. The remote works again once it reconnects."
    const val TYPE_HINT = "Select a text box on the TV, like a search box, then type here."

    fun cantType(text: String): String {
        val odd = text.firstOrNull { it !in ' '..'~' }
        return when {
            text.isEmpty() -> "Type something first."
            text.length > app.tellyscope.adb.Remote.MAX_TEXT -> "That's too long to send in one go. Send up to ${app.tellyscope.adb.Remote.MAX_TEXT} characters at a time."
            odd == '\n' || odd == '\t' -> "The TV can only take one line at a time."
            odd != null -> "The TV can only type plain letters, numbers and symbols, not \u201c$odd\u201d."
            else -> "The TV reads \u201c%s\u201d as a space, so it can't type that."
        }
    }

    const val REBOOT_TITLE = "Restart the TV?"
    const val REBOOT_TEXT = "Whatever is playing stops, and the TV takes a minute or two to come back. Tellyscope reconnects on its own when it's back."

    fun restarting(minutes: Long) =
        if (minutes < 3) "Tellyscope reconnects on its own when the TV is back, usually within a couple of minutes."
        else "Taking a while. Some TVs turn network debugging off when they restart. If it doesn't come back, turn it on again in the TV's Developer options."
}
