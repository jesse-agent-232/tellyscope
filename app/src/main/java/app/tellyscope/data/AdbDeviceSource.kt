// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.tellyscope.adb.ActionResult
import app.tellyscope.adb.ApkInfo
import app.tellyscope.adb.Files
import app.tellyscope.adb.InstallResult
import app.tellyscope.adb.Progress
import app.tellyscope.adb.Sideload
import app.tellyscope.adb.TvFile
import app.tellyscope.adb.TvFolder
import app.tellyscope.adb.Actions
import app.tellyscope.adb.Connector
import app.tellyscope.adb.DeviceLink
import app.tellyscope.adb.LabelHelper
import app.tellyscope.adb.LanScanner
import app.tellyscope.adb.Parsers
import app.tellyscope.adb.Protected
import app.tellyscope.adb.ProtectedReason
import app.tellyscope.adb.Remote
import app.tellyscope.adb.RemoteKey
import com.flyfishxu.kadb.exception.AdbPairAuthException
import com.flyfishxu.kadb.mdns.KadbMdnsAndroid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import okio.Sink
import okio.Source
import java.io.File
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import app.tellyscope.adb.Action as AdbAction
import app.tellyscope.adb.ConnectResult as AdbConnectResult
import app.tellyscope.adb.DeviceState as AdbState
import app.tellyscope.adb.ScanHit as AdbHit

/**
 * [DeviceSource] on real TVs through `:adb`. Device id is the TV's IP. Known devices live for the
 * app process only (persisting them is a later slice), and `procs` / network details beyond the
 * IP are empty for now: slice 1 is scan → connect → live CPU/RAM. App names and icons come from
 * the label helper through [AppNames].
 */
class AdbDeviceSource(context: Context) : DeviceSource {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob())
    private val links = ConcurrentHashMap<String, DeviceLink>()
    private val hits = ConcurrentHashMap<String, ScanHit>()
    private val store = DeviceStore(File(appContext.filesDir, "devices.tsv"))
    private val saved = ConcurrentHashMap<String, SavedDevice>()
    /** Remembered TVs that haven't answered since launch: shown offline, not "connecting". */
    private val restoring = ConcurrentHashMap.newKeySet<String>()
    private val watchers = ConcurrentHashMap<String, Job>()
    /** Address → [historyKey], so history follows a TV across DHCP changes. */
    private val keys = ConcurrentHashMap<String, String>()
    /** Wireless debugging advertises pairing and connect on different ports. */
    private val pairingPorts = ConcurrentHashMap<String, Int>()
    private val book = HistoryBook(File(appContext.filesDir, "history.tsv"))
    private val names = NameBook(File(appContext.filesDir, "names.tsv"))
    /** Last label seen per package, so history entries read like the app list. */
    private val labels = ConcurrentHashMap<String, String>()
    private val helperDex by lazy { appContext.assets.open("label-helper.jar").use { it.readBytes() } }
    private val appNames = AppNames(File(appContext.filesDir, "app-names"))
    private val lists = DeviceLists(File(appContext.filesDir, "lists"))
    /** [historyKey] → the keep entries of that TV's list, once it has been looked up. */
    private val keep = ConcurrentHashMap<String, Set<String>>()

    init {
        Connector.init(File(appContext.filesDir, "adbkey.pem"))
    }

    /** Remembered TVs reconnect in the background from launch; [known] waits for the file read. */
    private val restored = scope.async(Dispatchers.IO) {
        store.load().forEach { d ->
            saved[d.host] = d
            d.historyKey?.let { keys[d.host] = it }
            hits[d.host] = ScanHit(d.host, d.port, d.kind, d.name, null)
            restoring += d.host
            link(d.host, d.port, first = null, knownGood = true)
        }
    }

    /** Starts the reconnecting link for a TV and keeps its saved entry current as it comes and goes. */
    private fun link(host: String, port: Int, first: AdbConnectResult?, knownGood: Boolean) {
        var pending = first
        val link = DeviceLink(host, port, scope, knownGood = knownGood, open = { h, p ->
            pending?.also { pending = null } ?: Connector.connect(h, p)
        })
        links.put(host, link)?.disconnect()
        watchers.put(host, scope.launch {
            link.state.collect { st ->
                if (st == AdbState.ONLINE) {
                    restoring -= host
                    // Read again on every connect: a new TV may have taken over a saved address.
                    launch { readKey(host) }
                }
                if (st == AdbState.ONLINE || st == AdbState.OFFLINE) remember(host)
            }
        })?.cancel()
        link.connect()
    }

    private fun remember(host: String) {
        val link = links[host] ?: return
        val hit = hits[host] ?: return
        val old = saved[host]
        saved[host] = SavedDevice(
            host = host,
            port = link.port,
            kind = hit.kind,
            name = hit.hostname,
            model = link.stats.value?.model ?: old?.model,
            lastSeen = link.lastSeenMillis ?: old?.lastSeen ?: System.currentTimeMillis(),
            historyKey = keys[host] ?: old?.historyKey,
        )
        persist()
    }

    /** Reads the map at write time, so whichever save runs last writes the newest list. */
    private fun persist() {
        scope.launch(Dispatchers.IO) { synchronized(store) { store.save(saved.values.sortedBy { it.host }) } }
    }

    override suspend fun reconnect(deviceId: String) {
        restored.await()
        links[deviceId]?.connect()
    }

    override suspend fun rename(deviceId: String, name: String?) {
        restored.await()
        names.set(key(deviceId), name)
    }

    override suspend fun forget(deviceId: String) {
        restored.await()
        watchers.remove(deviceId)?.cancel()
        links.remove(deviceId)?.disconnect()
        hits.remove(deviceId)
        restoring -= deviceId
        if (saved.remove(deviceId) != null) persist()
    }

    /**
     * The subnet of the active network if it's Wi-Fi or Ethernet. On mobile data or a VPN there is
     * no sweep, so we never probe a carrier's or VPN's neighbours; mDNS and add-by-IP still work.
     */
    private fun lanHosts(): List<String> {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val network = cm.activeNetwork ?: return emptyList()
        val caps = cm.getNetworkCapabilities(network) ?: return emptyList()
        val local = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (!local || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return emptyList()
        return cm.getLinkProperties(network)?.linkAddresses.orEmpty()
            .filter { it.address is Inet4Address }
            .flatMap { LanScanner.subnetHosts(it.address as Inet4Address, it.prefixLength) }
            .distinct()
    }

    override fun scan(): Flow<ScanEvent> = flow {
        val hosts = lanHosts()
        if (hosts.isEmpty()) emit(ScanEvent.NoLan)
        LanScanner(KadbMdnsAndroid(appContext)).scan(hosts).collect { h ->
            if (h.kind == AdbHit.Kind.PAIRING) {
                pairingPorts[h.host] = h.port
                if (hits.containsKey(h.host)) return@collect
            }
            val hit = ScanHit(
                ip = h.host,
                port = h.port,
                kind = if (h.kind == AdbHit.Kind.CLASSIC) ScanKind.Classic else ScanKind.Wireless,
                hostname = h.name,
                maker = null, // Android 10+ apps can't read the ARP table
            )
            hits[h.host] = hit
            emit(ScanEvent.Found(hit))
        }
        emit(ScanEvent.Done)
    }

    override suspend fun connect(hit: ScanHit): ConnectResult = when (val r = Connector.connect(hit.ip, hit.port)) {
        is AdbConnectResult.Online -> {
            hits[hit.ip] = hit
            restoring -= hit.ip
            // Hand the open session to a link, which reconnects on its own from here on.
            link(hit.ip, hit.port, first = r, knownGood = false)
            ConnectResult.Online(device(hit.ip))
        }
        AdbConnectResult.NotAllowed -> ConnectResult.NotAllowed
        AdbConnectResult.NeedsPairing -> ConnectResult.NeedsPairing
        is AdbConnectResult.Unreachable -> ConnectResult.Unreachable(r.reason)
    }

    override suspend fun pair(hit: ScanHit, code: String): ConnectResult {
        try {
            Connector.pair(hit.ip, pairingPorts[hit.ip] ?: hit.port, code)
        } catch (e: AdbPairAuthException) {
            return ConnectResult.WrongCode(e.message ?: "pairing failed")
        } catch (e: IllegalArgumentException) {
            return ConnectResult.WrongCode(e.message ?: "pairing code must be 6 digits")
        }
        // After pairing, the connect port is the one mDNS advertised, not the pairing port.
        val connectHit = hits[hit.ip]?.takeIf { it.port != pairingPorts[hit.ip] } ?: hit
        return connect(connectHit)
    }

    override suspend fun known(): List<Device> {
        restored.await()
        return links.keys.map(::device)
    }

    override fun live(deviceId: String): Flow<Snapshot> {
        val link = links[deviceId] ?: return flow {}
        // Completes once the link drops (offline, not allowed, needs pairing), as the interface promises.
        return combine(link.state, link.stats) { state, stats -> state to stats }
            .transformWhile { (state, stats) ->
                if (state == AdbState.ONLINE && stats != null) emit(stats)
                state in setOf(AdbState.FOUND, AdbState.WAITING, AdbState.ONLINE)
            }
            .map { s ->
                val mem = s.memory
                Snapshot(
                    at = System.currentTimeMillis(),
                    cpuPercent = s.cpuPercent?.toInt() ?: 0,
                    ramUsedMb = mem?.let { ((it.totalKb - it.availableKb) / 1024).toInt() } ?: 0,
                    ramTotalMb = mem?.let { (it.totalKb / 1024).toInt() } ?: 0,
                    storageUsedGb = s.storage?.let { it.usedKb / 1_048_576.0 },
                    storageTotalGb = s.storage?.let { it.totalKb / 1_048_576.0 },
                    tempC = s.temperatureC?.toInt(),
                    uptimeSeconds = s.uptimeSeconds ?: 0,
                    screenOn = null,
                    procs = emptyList(),
                )
            }
    }

    override suspend fun apps(deviceId: String): List<AppInfo> {
        val shell = session(deviceId)
        val home = Actions(shell).homePackages()
        val system = Parsers.packages(shell.shell("pm list packages -s").output).map { it.name }.toSet()
        val disabled = Parsers.packages(shell.shell("pm list packages -d").output).map { it.name }.toSet()
        // Older pm rejects --show-versioncode and prints no packages; list again without it.
        val listed = Parsers.packages(shell.shell("pm list packages --show-versioncode").output)
            .ifEmpty { Parsers.packages(shell.shell("pm list packages").output) }
        // Same key as history: a placeholder serial must not give two boxes one cache.
        val names = appNames.names(key(deviceId), listed, LabelHelper(shell, helperDex)::describe)
        val keep = keep[key(deviceId)].orEmpty()
        return listed.map { p ->
            val name = names.getValue(p.name)
            labels[p.name] = name.label
            AppInfo(
                packageName = p.name,
                label = name.label,
                iconColor = null,
                system = p.name in system,
                enabled = p.name !in disabled,
                // Same rule as Actions.run: no known launcher, nothing can be stopped or disabled.
                // A disabled app stays unprotected so Enable (always allowed) still shows.
                protectedReason = withKeep(
                    when {
                        home.isNotEmpty() -> Protected.reason(p.name, home)
                        p.name in disabled -> null
                        else -> ProtectedReason.UNKNOWN_HOME
                    }?.let(::reasonText),
                    p.name, enabled = p.name !in disabled, keep,
                ),
                version = "",
                icon = name.icon,
            )
        }.sortedBy { it.label.lowercase() }
    }

    override suspend fun network(deviceId: String): NetworkInfo {
        val out = session(deviceId).shell("ip -o -4 addr show").output
        val line = out.lineSequence().firstOrNull { "inet " in it && " lo " !in it }
        val f = line?.trim()?.split(Regex("\\s+")).orEmpty()
        return NetworkInfo(
            iface = f.getOrElse(1) { "" },
            ip = f.getOrNull(3)?.substringBefore('/') ?: deviceId,
            mac = null, ssid = null, rssi = null, linkMbps = null,
            sockets = emptyList(), traffic = emptyList(),
        )
    }

    override suspend fun act(deviceId: String, action: Action, packageName: String, undoing: Long?): HistoryEntry {
        val shell = session(deviceId)
        // Before the first change on a TV, note what was already off: Restore must leave those alone.
        val key = key(deviceId)
        if (!book.hasBaseline(key)) book.setBaseline(key, disabledPackages(deviceId))
        // The list's keep entries add to the built-in protection in Actions.run; look it up if not yet.
        if (action == Action.Stop || action == Action.Disable) {
            if (keepRefuses(action, packageName, keep[key] ?: resolve(deviceId).list?.keep.orEmpty())) {
                throw IllegalStateException(KEEP_REASON)
            }
        }
        val r = Actions(shell).run(AdbAction.valueOf(action.name.uppercase()), packageName)
        return when (r) {
            is ActionResult.Done -> book.record(
                deviceId = key,
                at = System.currentTimeMillis(),
                action = action,
                packageName = packageName,
                label = labels[packageName] ?: AppNames.fallback(packageName),
                undo = r.undo?.let { Action.valueOf(it.name.lowercase().replaceFirstChar(Char::uppercase)) },
                undoing = undoing,
            )
            is ActionResult.Refused -> throw IllegalStateException(reasonText(r.reason))
            is ActionResult.Failed -> throw IllegalStateException(r.output.ifEmpty { "the TV refused" })
        }
    }

    override suspend fun history(deviceId: String): List<HistoryEntry> = book.entries(key(deviceId))

    override suspend fun restoreAll(deviceId: String): List<HistoryEntry> {
        // Only what is still off now: the user may have turned some back on from the TV itself.
        val off = disabledPackages(deviceId)
        val key = key(deviceId)
        return restoreEach(book.restorable(key).filter { it in off }) { pkg ->
            act(deviceId, Action.Enable, pkg, undoing = book.openDisable(key, pkg))
        }
    }

    override suspend fun suggestions(deviceId: String): Suggestions = try {
        val r = resolve(deviceId)
        when {
            r.index == null -> Suggestions.Unavailable
            r.entry == null -> Suggestions.Pick(r.index.commit, r.index.devices)
            r.list == null -> Suggestions.Unavailable
            else -> Suggestions.Ready(r.index.commit, r.list, r.sdk, suggest(r.list, apps(deviceId), r.sdk), r.index.devices)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Suggestions.Unavailable // the TV dropped mid-read
    }

    override suspend fun pickList(deviceId: String, listId: String) {
        val key = key(deviceId)
        lists.pick(key, listId)
        keep.remove(key)
    }

    private class Resolved(val index: ListIndex?, val entry: ListEntry?, val list: DeviceList?, val sdk: Int?)

    /** This TV's list (picked, else matched), with its keep entries recorded for [apps] and [act]. */
    private suspend fun resolve(deviceId: String): Resolved {
        val index = lists.index() ?: return Resolved(null, null, null, null)
        val out = session(deviceId).shell(
            (MATCH_KEYS.map { "ro.product.$it" } + "ro.build.version.sdk").joinToString("; ") { "getprop $it" },
        ).output.lines().map { it.trim().ifEmpty { null } }
        val props = MATCH_KEYS.withIndex().associate { (i, k) -> k to out.getOrNull(i) }
        val key = key(deviceId)
        val entry = lists.entryFor(index, key, props)
        val list = entry?.let { lists.list(it) }
        keep[key] = list?.keep.orEmpty()
        return Resolved(index, entry, list, out.getOrNull(MATCH_KEYS.size)?.toIntOrNull())
    }

    override suspend fun press(deviceId: String, key: RemoteKey) = Remote(session(deviceId)).press(key)

    override suspend fun type(deviceId: String, text: String) = Remote(session(deviceId)).type(text)

    override suspend fun reboot(deviceId: String) = Remote(session(deviceId)).reboot()

    /**
     * The history key for a TV, read from it once connected and saved with it. Until it has
     * answered (offline since install) the address stands in, and that history moves over when
     * the key is learned.
     */
    private suspend fun key(deviceId: String): String = keys[deviceId] ?: readKey(deviceId)

    private suspend fun readKey(deviceId: String): String {
        val shell = links[deviceId]?.session ?: return keys[deviceId] ?: deviceId
        suspend fun prop(cmd: String) = shell.shell(cmd).takeIf { it.exitCode == 0 }?.output
        val key = try {
            historyKey(prop("getprop ro.serialno"), prop("settings get secure android_id"), deviceId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return keys[deviceId] ?: deviceId
        }
        if (keys.put(deviceId, key) != key) {
            book.rekey(deviceId, key)
            names.rekey(deviceId, key)
            remember(deviceId)
        }
        return key
    }

    private suspend fun disabledPackages(deviceId: String) =
        Parsers.packages(session(deviceId).shell("pm list packages -d").output).map { it.name }.toSet()

    override suspend fun installed(deviceId: String, packageName: String) = Sideload(session(deviceId)).installed(packageName)

    override suspend fun install(
        deviceId: String,
        apks: List<File>,
        apk: ApkInfo,
        allowDowngrade: Boolean,
        onProgress: (Progress) -> Unit,
    ): InstallResult = Sideload(session(deviceId)).install(apks, apk, allowDowngrade, onProgress)

    override suspend fun files(deviceId: String, folder: TvFolder) = Files(session(deviceId)).list(folder)

    override suspend fun push(deviceId: String, folder: TvFolder, name: String, size: Long?, source: Source, onProgress: (Progress) -> Unit) =
        Files(session(deviceId)).push(source, size, name, folder, onProgress)

    override suspend fun pull(deviceId: String, folder: TvFolder, file: TvFile, sink: Sink, onProgress: (Progress) -> Unit) =
        Files(session(deviceId)).pull(folder, file, sink, onProgress)

    private fun session(deviceId: String) =
        links[deviceId]?.session ?: throw IllegalStateException("$deviceId is offline")

    private fun device(id: String): Device {
        val link = links[id]
        val stats = link?.stats?.value
        val state = link?.state?.value
        return Device(
            id = id,
            hit = hits.getValue(id),
            // Still waking up after launch reads as offline; a refusal shows as itself.
            state = if (id in restoring && state in setOf(AdbState.FOUND, AdbState.WAITING, AdbState.OFFLINE)) {
                DeviceState.Offline
            } else when (state) {
                AdbState.WAITING -> DeviceState.Waiting
                AdbState.ONLINE -> DeviceState.Online
                AdbState.OFFLINE -> DeviceState.Offline
                AdbState.NOT_ALLOWED -> DeviceState.NotAllowed
                AdbState.NEEDS_PAIRING -> DeviceState.NeedsPairing
                AdbState.FOUND, null -> DeviceState.Found
            },
            model = stats?.model ?: saved[id]?.model,
            manufacturer = stats?.manufacturer,
            android = stats?.androidVersion,
            lastSeen = link?.lastSeenMillis ?: saved[id]?.lastSeen,
            nickname = names[keys[id] ?: id],
        )
    }

    /** Copy from the protected-apps table in the UX copy doc. */
    private fun reasonText(r: ProtectedReason) = when (r) {
        ProtectedReason.HOME_SCREEN -> "this is your home screen"
        ProtectedReason.SYSTEM -> "the TV needs this to work"
        ProtectedReason.SETTINGS -> "you need this to change TV settings"
        ProtectedReason.PLAY_SERVICES -> "most apps stop working without it"
        ProtectedReason.KEYBOARD -> "you'd lose the on-screen keyboard"
        ProtectedReason.TV_INPUTS_REMOTE -> "your remote or HDMI inputs could stop working"
        ProtectedReason.SETUP_WIZARD -> "the TV may get stuck on restart without it"
        ProtectedReason.UNKNOWN_HOME -> "this TV didn't say which app is its home screen, so Tellyscope won't change anything"
    }
}
