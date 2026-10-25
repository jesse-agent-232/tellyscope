// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import java.net.URLEncoder

/** The phone side of a report: Tellyscope's version and the phone it runs on, e.g. "Pixel 7, Android 14". */
data class AppFacts(val version: String, val phone: String)

/**
 * The TV side of a report. [stockPackages] are the apps the TV came with (`pm list packages -s`);
 * user-installed apps are only counted, in [userApps], so a report never lists what someone installed.
 */
data class TvFacts(
    val manufacturer: String?,
    val model: String?,
    val device: String?,
    val androidVersion: String?,
    val sdk: Int?,
    val build: String?,
    val stockPackages: List<String>,
    val userApps: Int,
    /** Values that identify this one TV, cut from anything a report quotes. Never printed. */
    val identifiers: Set<String>,
) {
    /** "XGIMI XK03H (codename)", or what's known of it. */
    val name: String
        get() = listOfNotNull(manufacturer, model).joinToString(" ").ifEmpty { "Unknown TV" } +
            (device?.takeIf { it != model }?.let { " ($it)" } ?: "")

    /** "Android 11 (API 30), build RTT1.230922.001". */
    val android: String
        get() = listOfNotNull(
            androidVersion?.let { v -> "Android $v" + (sdk?.let { " (API $it)" } ?: "") },
            build?.let { "build $it" },
        ).joinToString(", ").ifEmpty { "Unknown" }

    companion object {
        suspend fun read(shell: AdbShell): TvFacts {
            val props = Parsers.props(shell.shell("getprop").output)
            val stockNames = listOfNotNull(props["ro.product.model"], props["ro.product.device"], props["ro.product.manufacturer"])
            val stock = listOfNotNull(props["ro.product.manufacturer"], props["ro.product.model"]).joinToString(" ")
            return TvFacts(
                manufacturer = props["ro.product.manufacturer"]?.ifBlank { null },
                model = props["ro.product.model"]?.ifBlank { null },
                device = props["ro.product.device"]?.ifBlank { null },
                androidVersion = props["ro.build.version.release"]?.ifBlank { null },
                sdk = props["ro.build.version.sdk"]?.toIntOrNull(),
                build = (props["ro.build.display.id"] ?: props["ro.build.id"])?.ifBlank { null },
                stockPackages = Parsers.packages(shell.shell(Commands.SYSTEM_PACKAGES).output).map { it.name }.sorted(),
                userApps = Parsers.packages(shell.shell(Commands.USER_PACKAGES).output).size,
                // A device name the owner never changed is just the model, which the report needs.
                identifiers = IDENTIFYING_PROPS.mapNotNull { props[it]?.trim() }
                    .filter { it.length >= 6 && it != "unknown" && !it.equals(stock, ignoreCase = true) && it !in stockNames }
                    .toSet(),
            )
        }

        private val IDENTIFYING_PROPS = listOf("ro.serialno", "ro.boot.serialno", "persist.sys.device_name", "net.hostname")
    }
}

/**
 * A GitHub issue to open from the phone, pre-filled through the form's query parameters. Nothing is
 * sent: the user reads [text], then posts it from their own GitHub login. When [link] would be too
 * long, it leaves [overflowField] empty and the user pastes [clipboard] into it.
 */
data class Report(
    val repo: String,
    val template: String,
    val title: String,
    /** Form field id to value, in the form's order. */
    val fields: Map<String, String>,
    /** Field id to the label the form shows, for [text]. */
    val labels: Map<String, String>,
    val overflowField: String,
) {
    /** What the user sees before anything opens: the title and every field. */
    val text: String
        get() = buildString {
            append(title).append("\n")
            fields.forEach { (id, value) -> append("\n### ").append(labels[id] ?: id).append("\n\n").append(value).append("\n") }
        }

    /** Whether the whole report fits in the link; if not, the UI says "Copy and open GitHub". */
    val fits: Boolean get() = url(fields).length <= MAX_URL

    /** The form, pre-filled with everything, or everything but [overflowField] when it doesn't [fit][fits]. */
    val link: String get() = if (fits) url(fields) else url(fields - overflowField)

    /** For the clipboard when it doesn't [fit][fits]: the field the link leaves out. */
    val clipboard: String? get() = if (fits) null else fields[overflowField]

    private fun url(values: Map<String, String>): String =
        "https://github.com/$repo/issues/new?" + (listOf("template" to template, "title" to title) + values.toList())
            .joinToString("&") { (k, v) -> "$k=${encode(v)}" }

    companion object {
        /** GitHub answers 414 a little past 8 KB; this leaves room for redirects through a login. */
        const val MAX_URL = 6000

        private fun encode(s: String) = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")
    }
}

/** Builds the two reports: problems go to Tellyscope's tracker, an unsupported TV to cleanyourtv's. */
object Reports {
    const val OURS = "man15h/tellyscope"
    const val CLEANYOURTV = "Gathin23/cleanyourtv"

    /** The options of our device_report.yml "How did you connect?" dropdown, word for word. */
    enum class Connection(val option: String) {
        PORT_5555("Port 5555 (classic network debugging)"),
        WIRELESS_DEBUGGING("Wireless debugging (pairing code)"),
        COULD_NOT_CONNECT("Couldn't connect"),
    }

    /** Our device_report.yml "What happened?" options. */
    enum class Outcome(val option: String) {
        WORKED("Everything worked"),
        MISBEHAVED("Connected, but something misbehaved"),
        COULD_NOT_CONNECT("Could not connect"),
        PACKAGE_PROBLEM("Disabling a package caused a problem"),
    }

    /** cleanyourtv's tv-list.yml "How far did you get?" options. */
    enum class Stage(val option: String) {
        COULD_NOT_CONNECT("Could not connect over ADB"),
        LAUNCHER_FAILED("Debloat worked, launcher did not"),
        HERE_IS_MY_LIST("Both worked, here is my list"),
        BROKE_AFTER("Something broke after the debloat"),
    }

    /** "Report a problem": our device_report.yml. [log] is the app's log; [tv] is null when it never connected. */
    fun problem(app: AppFacts, tv: TvFacts?, connection: Connection, outcome: Outcome, details: String, log: String?): Report {
        val redact = { s: String -> Redact.text(s, tv?.identifiers.orEmpty()) }
        val body = buildString {
            append(redact(details.trim()).ifEmpty { "(nothing written)" })
            append("\n\nTellyscope ${app.version} on ${redact(app.phone)}")
            log?.let { append("\n\nLog:\n```\n").append(redact(it.trim())).append("\n```") }
        }
        return Report(
            repo = OURS,
            template = "device_report.yml",
            title = "[Device] ${tv?.name ?: "Unknown TV"}",
            fields = linkedMapOf(
                "model" to (tv?.name ?: "Unknown TV"),
                "android-version" to (tv?.android ?: "Unknown"),
                "connection" to connection.option,
                "app-version" to app.version,
                "outcome" to outcome.option,
                "details" to body,
            ),
            labels = mapOf(
                "model" to "TV make and model",
                "android-version" to "Android TV / Google TV version",
                "connection" to "How did you connect?",
                "app-version" to "Tellyscope version",
                "outcome" to "What happened?",
                "details" to "Details",
            ),
            overflowField = "details",
        )
    }

    /** "Help add this TV": cleanyourtv's tv-list.yml, with the apps the TV came with. */
    fun newTv(app: AppFacts, tv: TvFacts, stage: Stage, details: String): Report {
        val body = buildString {
            Redact.text(details.trim(), tv.identifiers).takeIf { it.isNotEmpty() }?.let { append(it).append("\n\n") }
            append("Apps the TV came with (${tv.stockPackages.size}):\n")
            tv.stockPackages.forEach { append(it).append("\n") }
            append("\nUser-installed apps: ${tv.userApps} (not listed)\n")
            append("Sent from Tellyscope ${app.version}")
        }
        return Report(
            repo = CLEANYOURTV,
            template = "tv-list.yml",
            title = "[TV] ${listOfNotNull(tv.manufacturer, tv.model).joinToString(" ").ifEmpty { "Unknown TV" }}" +
                (tv.androidVersion?.let { " (Android $it)" } ?: ""),
            fields = linkedMapOf(
                "model" to tv.name,
                "android" to tv.android,
                "stage" to stage.option,
                "details" to body,
            ),
            labels = mapOf("model" to "TV model", "android" to "Android version and firmware build", "stage" to "How far did you get?", "details" to "What happened"),
            overflowField = "details",
        )
    }
}

/**
 * Cuts what could identify a person or a TV from text a user is about to post: IP and MAC
 * addresses, e-mail addresses, ADB keys, key fingerprints, pairing codes, Wi-Fi names and serial numbers.
 * It errs toward cutting: a four-part version number reads as an IP address and goes too.
 */
object Redact {
    private val RULES = listOf(
        // A whole ADB key or PEM block, before anything inside it can match a shorter rule.
        Regex("-----BEGIN [A-Z ]+-----[\\s\\S]*?-----END [A-Z ]+-----") to "<key>",
        Regex("QAAAA[A-Za-z0-9+/=]{100,}(\\s+\\S+@\\S+)?") to "<adb key>",
        Regex("[A-Za-z0-9+/]{200,}={0,2}") to "<key>",
        Regex("(?i)\\b([0-9a-f]{2}:){15,31}[0-9a-f]{2}\\b") to "<fingerprint>",
        Regex("(?i)\\b[0-9a-f]{40,64}\\b") to "<fingerprint>",
        Regex("(?i)\\b([0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b") to "<mac>",
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") to "<email>",
        // mDNS names carry the TV's serial: adb-<serial>-<random>._adb-tls-connect._tcp
        // The service types (_adb-tls-connect, _adb-tls-pairing) say which step it was, so they stay.
        Regex("(?<![\\w-])adb-(?!tls-)[A-Za-z0-9]+-[A-Za-z0-9]+") to "adb-<serial>",
        // A word of its own with a separator, so NotSerializableException and SerialExecutor stay.
        Regex("(?i)\\b(serial(?:no|\\s+number)?(?:\\s*[:=]\\s*|\\s+))[A-Za-z0-9]{6,}") to "$1<serial>",
        // "code" on its own: versionCode=310100 stays.
        Regex("(?i)(\\b(?:pairing\\s+)?code\\b\\s*[:=]?\\s*)\\d{6}\\b") to "$1<code>",
        Regex("(?i)(\\bssid\\s*[:=]?\\s*)(\"[^\"\\n]*\"|\\S+)") to "$1<wifi>",
        // A full stop after it is the end of a sentence; a digit after one is a longer version number.
        Regex("(?<![\\w.])(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}(?!\\w|\\.\\d)") to "<ip>",
        // IPv6 only in full or with "::", so a log's 12:30:45 stays.
        Regex("(?i)(?<![\\w:])([0-9a-f]{1,4}:){7}[0-9a-f]{1,4}(?![\\w:])") to "<ip>",
        Regex("(?i)(?<![\\w:])([0-9a-f]{1,4}:)*[0-9a-f]{0,4}::([0-9a-f]{1,4}:)*[0-9a-f]{0,4}(%\\w+)?(?![\\w:])") to "<ip>",
    )

    /** [identifiers] are this TV's own values (its serial, its name), cut wherever they appear. */
    fun text(s: String, identifiers: Set<String> = emptySet()): String {
        var out = identifiers.sortedByDescending { it.length }.fold(s) { acc, id -> acc.replace(id, "<serial>") }
        for ((rule, with) in RULES) out = rule.replace(out, with)
        return out
    }
}
