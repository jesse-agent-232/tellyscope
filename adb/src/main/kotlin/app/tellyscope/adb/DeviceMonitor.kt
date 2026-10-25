// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.YearMonth
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** One reading for the stat strip. Null fields mean "this TV doesn't report it". */
data class Stats(
    val cpuPercent: Double?,
    val memory: Parsers.Memory?,
    val storage: Parsers.Storage? = null,
    val uptimeSeconds: Long? = null,
    val temperatureC: Double? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    /** The name on the box; the manufacturer can be the factory (Philips TVs say TPV). */
    val brand: String? = null,
    val androidVersion: String? = null,
    val sdk: Int? = null,
    /** Uid → packages, from the slow tier; empty where `pm` can't list uids. */
    val packages: Map<Int, List<String>> = emptyMap(),
    val processes: List<AppProcess> = emptyList(),
    val screenOn: Boolean? = null,
    /** Bytes in and out per second over the whole TV, between the last two fast readings. */
    val netBytesPerSec: Long? = null,
    val softwareMonth: YearMonth? = null,
)

/** One process and the package it belongs to; null for native daemons. */
data class AppProcess(val pid: Int, val name: String, val packageName: String?, val cpuPercent: Double?, val rssKb: Long)

/** Where [DeviceLink] gets readings from; [DeviceMonitor] in the app, a fake in tests. */
fun interface MonitorSource {
    fun stats(): Flow<Stats>
}

/** How much of the process list a screen needs. */
enum class ProcessLevel {
    /** Nothing shown: no process read; the last list is kept. */
    NONE,

    /** Memory per app (Overview, Apps): `ps`, no CPU and no screen state. */
    LIGHT,

    /** CPU per process and the screen state (Advanced): `top` and `dumpsys power`. */
    FULL,
}

/**
 * What the app tells one TV's stats loop. [during] pauses it while a change runs, so a slow read
 * never competes with `pm`, an install or a Home switch; [processes] says how much of the process
 * list the open screen shows.
 */
class StatsGate {
    private val running = MutableStateFlow(0)

    val processes = MutableStateFlow(ProcessLevel.FULL)

    val busy: Boolean get() = running.value > 0

    suspend fun <T> during(block: suspend () -> T): T {
        running.update { it + 1 }
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }

    internal suspend fun awaitIdle() {
        running.first { it == 0 }
    }
}

/**
 * Live stats on three tiers: CPU/RAM/network speed every [fast], processes at [StatsGate.processes]'s
 * level every [medium] and at once when it changes, identity/storage/uptime/temperature/packages every [slow].
 * The first emission comes after one fast interval, since CPU % needs two samples.
 * A read slower than [readTimeout] skips that sample. Shell failures throw, and so does a slow read
 * the TV then doesn't follow with an `echo` answer within [pingTimeout], so the caller can mark it
 * offline instead of waiting out the socket timeout.
 */
class DeviceMonitor(
    private val shell: AdbShell,
    private val fast: Duration = 2.seconds,
    private val medium: Duration = 10.seconds,
    private val slow: Duration = 60.seconds,
    private val readTimeout: Duration = 10.seconds,
    private val pingTimeout: Duration = 10.seconds,
    private val gate: StatsGate = StatsGate(),
    /** Times network speed; tests pass the test scheduler's clock. */
    private val clock: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) : MonitorSource {
    override fun stats(): Flow<Stats> = flow {
        var slowStats = Stats(cpuPercent = null, memory = null)
        var lastSlow: TimeMark? = null
        var mediumStats: Pair<List<AppProcess>, Boolean?> = emptyList<AppProcess>() to null
        var lastMedium: TimeMark? = null
        var lastLevel = ProcessLevel.NONE
        var prev: Fast? = null

        while (true) {
            // A skipped tier waits its whole interval again, so a TV that is always slow isn't asked every 2 s.
            if (lastSlow.due(slow)) {
                readSlow()?.let { slowStats = it }
                lastSlow = TimeSource.Monotonic.markNow()
            }
            val level = gate.processes.value
            if (level != ProcessLevel.NONE && (level != lastLevel || lastMedium.due(medium))) {
                readProcesses(level, slowStats.packages, mediumStats.second)?.let { mediumStats = it }
                lastMedium = TimeSource.Monotonic.markNow()
                lastLevel = level
            }
            val now = readFast()
            val last = prev
            if (now != null && last != null) {
                val pct = if (last.cpu != null && now.cpu != null) Parsers.cpuPercent(last.cpu, now.cpu) else null
                emit(
                    slowStats.copy(
                        cpuPercent = pct,
                        memory = now.memory,
                        processes = mediumStats.first,
                        screenOn = mediumStats.second,
                        netBytesPerSec = rate(last, now),
                    ),
                )
            }
            if (now != null) prev = now
            delay(fast)
        }
    }

    private fun TimeMark?.due(every: Duration) = this == null || elapsedNow() >= every

    /**
     * Null skips the sample: the read ran past [readTimeout] while a change was running, or the TV
     * still answered `echo` after it. While a change runs nothing is read, but the TV is pinged
     * every [medium], so one that dies mid-change still ends the change instead of hanging it.
     */
    private suspend fun run(command: String): String? {
        while (withTimeoutOrNull(medium) { gate.awaitIdle() } == null) ping()
        withTimeoutOrNull(readTimeout) { shell.shell(command).output }?.let { return it }
        if (!gate.busy) ping()
        return null
    }

    private suspend fun ping() {
        withTimeoutOrNull(pingTimeout) { shell.shell("echo") } ?: throw IOException("no answer to echo in $pingTimeout")
    }

    private class Fast(val cpu: Parsers.CpuTimes?, val memory: Parsers.Memory?, val netBytes: Long?, val at: ComparableTimeMark)

    private suspend fun readFast(): Fast? {
        val (stat, meminfo, netDev) = Commands.split(run(Commands.FAST) ?: return null, 3)
        return Fast(Parsers.cpuTimes(stat), Parsers.memory(meminfo), Parsers.netBytes(netDev), clock.markNow())
    }

    /** Null across a counter reset (an interface going down and up), never a negative speed. */
    private fun rate(prev: Fast, now: Fast): Long? {
        val bytes = (now.netBytes ?: return null) - (prev.netBytes ?: return null)
        val ms = (now.at - prev.at).inWholeMilliseconds
        return if (bytes < 0 || ms <= 0) null else bytes * 1000 / ms
    }

    /** The light read keeps [screenOn] from the last full one; only Advanced shows it. */
    private suspend fun readProcesses(level: ProcessLevel, packages: Map<Int, List<String>>, screenOn: Boolean?): Pair<List<AppProcess>, Boolean?>? {
        val (procs, screen) = if (level == ProcessLevel.FULL) {
            val (top, power) = Commands.split(run(Commands.MEDIUM) ?: return null, 2)
            Parsers.processes(top) to Parsers.screenOn(power)
        } else {
            (readPs() ?: return null) to screenOn
        }
        val known = packages.values.flatten().toSet()
        return procs.map { p -> AppProcess(p.pid, p.name, Parsers.owner(p, packages, known), p.cpuPercent, p.rssKb) } to screen
    }

    /** Set once `ps -o` printed nothing usable; plain `ps` from then on. */
    private var plainPs = false

    private suspend fun readPs(): List<Parsers.Process>? {
        if (!plainPs) {
            val procs = Parsers.psProcesses(run(Commands.LIGHT) ?: return null)
            if (procs.isNotEmpty()) return procs
            plainPs = true
        }
        return Parsers.psProcesses(run("ps") ?: return null)
    }

    private suspend fun readSlow(): Stats? {
        val (getprop, df, uptime, thermal, pm) = Commands.split(run(Commands.SLOW) ?: return null, 5)
        val props = Parsers.props(getprop)
        return Stats(
            cpuPercent = null,
            memory = null,
            storage = Parsers.storage(df),
            uptimeSeconds = Parsers.uptimeSeconds(uptime),
            temperatureC = Parsers.temperatureC(thermal),
            model = props["ro.product.model"],
            manufacturer = props["ro.product.manufacturer"],
            brand = props["ro.product.brand"],
            androidVersion = props["ro.build.version.release"],
            sdk = props["ro.build.version.sdk"]?.toIntOrNull(),
            softwareMonth = Parsers.softwareMonth(props),
            packages = Parsers.byUid(Parsers.packages(pm)),
        )
    }
}
