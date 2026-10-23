// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

/** Another device's "Allow debugging?" prompt on the TV, with the key fingerprint it shows. */
data class ConnectionRequest(
    val fingerprint: String,
    /** The prompt has an "Always allow" tick-box; some builds leave it out. */
    val canAlwaysAllow: Boolean = true,
)

/**
 * Answers the TV's own "Allow debugging?" prompt for another device, from the phone. Tellyscope
 * never decides: it reads the prompt off the screen, shows its fingerprint, and presses the
 * button the user picked. "Always allow" is only ticked when the user ticks it.
 */
class ConnectionPrompt(private val shell: AdbShell) {

    /** The prompt on screen now, or null. Cheap enough to poll: one `dumpsys window` line. */
    suspend fun pending(): ConnectionRequest? {
        if (showing() == null) return null
        return screen()?.let { ConnectionRequest(it.fingerprint, canAlwaysAllow = it.always != null) }
    }

    /**
     * Presses Allow or Cancel on the prompt, but only while it still shows [request]'s
     * fingerprint: a different device's prompt may have replaced it. False when it no longer does.
     */
    suspend fun answer(request: ConnectionRequest, allow: Boolean, always: Boolean): Boolean {
        if (showing() == null) return false
        val s = screen()?.takeIf { it.fingerprint == request.fingerprint } ?: return false
        if (allow && s.always != null && s.always.checked != always) tap(s.always.bounds)
        val button = (if (allow) s.allow else s.cancel) ?: return false
        return tap(button)
    }

    /** The focused window's package when it's the system's debugging prompt. */
    private suspend fun showing(): String? =
        focused(shell.shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedWindow'").output)

    private suspend fun screen(): Prompt? = parse(shell.shell("uiautomator dump $DUMP >/dev/null 2>&1; cat $DUMP; rm -f $DUMP").output)

    private suspend fun tap(b: Bounds) = shell.shell("input tap ${(b.left + b.right) / 2} ${(b.top + b.bottom) / 2}").exitCode == 0

    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
    data class Box(val bounds: Bounds, val checked: Boolean)
    data class Prompt(val fingerprint: String, val allow: Bounds?, val cancel: Bounds?, val always: Box?)

    companion object {
        private const val DUMP = "/data/local/tmp/tellyscope-ui.xml"
        private val NODE = Regex("<node [^>]*>")
        private val ATTR = Regex("([\\w-]+)=\"([^\"]*)\"")
        private val BOUNDS = Regex("\\[(\\d+),(\\d+)]\\[(\\d+),(\\d+)]")
        /** adb shows the key's MD5 as 16 colon-separated hex pairs. */
        private val FINGERPRINT = Regex("(?:[0-9A-Fa-f]{2}:){15}[0-9A-Fa-f]{2}")

        /**
         * The prompt in a `uiautomator dump`: AOSP's AlertDialog ids, from [SYSTEM] packages only.
         * Null when there is no fingerprint.
         */
        /**
         * Where the system draws the prompt: System UI on stock Android, and TV builds may move
         * it. Only these count, so an app with a look-alike screen is never answered.
         */
        private val SYSTEM = setOf("com.android.systemui", "android", "com.android.tv.settings")
        private val WINDOW = Regex("\\s([\\w.]+)/[\\w.$]*UsbDebugging[\\w.$]*")

        /** The package of a focused `…/…UsbDebugging…` window in `dumpsys window`, if a system one. */
        fun focused(dumpsys: String): String? = WINDOW.find(dumpsys)?.groupValues?.get(1)?.takeIf { it in SYSTEM }

        fun parse(xml: String): Prompt? {
            val nodes = NODE.findAll(xml).map { n -> ATTR.findAll(n.value).associate { it.groupValues[1] to it.groupValues[2] } }
                .filter { it["package"] in SYSTEM }.toList()
            fun id(name: String) = nodes.firstOrNull { it["resource-id"] == "android:id/$name" }
            fun bounds(n: Map<String, String>?) = n?.get("bounds")?.let(BOUNDS::matchEntire)?.groupValues
                ?.let { (_, l, t, r, b) -> Bounds(l.toInt(), t.toInt(), r.toInt(), b.toInt()) }
            val fingerprint = id("message")?.get("text")?.let(FINGERPRINT::find)?.value?.uppercase() ?: return null
            val always = id("alwaysUse")
            return Prompt(
                fingerprint = fingerprint,
                allow = bounds(id("button1")),
                cancel = bounds(id("button2")),
                always = bounds(always)?.let { Box(it, always?.get("checked") == "true") },
            )
        }
    }
}
