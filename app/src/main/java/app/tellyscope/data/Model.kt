// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import java.time.YearMonth

/** How a TV was found: legacy network ADB on 5555, or Android 11+ wireless debugging (TLS + pairing code). */
enum class ScanKind { Classic, Wireless }

data class ScanHit(
    val ip: String,
    val port: Int,
    val kind: ScanKind,
    /** mDNS name, else reverse DNS, else null. */
    val hostname: String?,
    /** From the MAC's OUI; null when the MAC is randomised. */
    val maker: String?,
)

data class ScanProgress(val probed: Int, val total: Int, val subnet: String)

sealed interface ScanEvent {
    data class Found(val hit: ScanHit) : ScanEvent
    data class Progress(val progress: ScanProgress) : ScanEvent
    /** Not on Wi-Fi or Ethernet: only TVs that announce themselves (or add-by-IP) can be found. */
    data object NoLan : ScanEvent
    data object Done : ScanEvent
}

/**
 * ADB reports `unauthorized` both before the user answers and after they press
 * Deny, so there is no "denied" state: [NotAllowed] means "unauthorized past the
 * timeout", and only on a first connect. A TV that was online before and stalls
 * stays [Offline] and keeps retrying. [NeedsPairing] is a wireless-debugging TV
 * that doesn't know our key yet: show the 6-digit code field.
 */
enum class DeviceState { Found, Waiting, NeedsPairing, Online, Offline, NotAllowed }

data class Device(
    val id: String,
    val hit: ScanHit,
    val state: DeviceState,
    val model: String?,
    val manufacturer: String?,
    val android: String?,
    /** Epoch millis of the last successful poll. */
    val lastSeen: Long?,
    /** The name the user gave it; wins over the network name. */
    val nickname: String? = null,
)

sealed interface ConnectResult {
    data class Online(val device: Device) : ConnectResult
    data object NotAllowed : ConnectResult
    /** Wireless-debugging TV that doesn't know our key; ask for the pairing code. */
    data object NeedsPairing : ConnectResult
    data class WrongCode(val message: String) : ConnectResult
    data class Unreachable(val message: String) : ConnectResult
}

data class Proc(
    val pid: Int,
    val name: String,
    /** Owning package; null for native daemons (shown as "android system"). */
    val packageName: String?,
    val cpuPercent: Double,
    val ramMb: Int,
)

/** One poll of the fast tier. Null fields are stats the device doesn't report; the UI shows "—", never 0. */
data class Snapshot(
    val at: Long,
    val cpuPercent: Int,
    val ramUsedMb: Int,
    val ramTotalMb: Int,
    val storageUsedGb: Double?,
    val storageTotalGb: Double?,
    val tempC: Int?,
    val uptimeSeconds: Long,
    val screenOn: Boolean?,
    val procs: List<Proc>,
    /** Whole-TV network traffic, in and out. */
    val netBytesPerSec: Long? = null,
    /** When the TV's software was last updated. */
    val softwareMonth: YearMonth? = null,
)

data class AppInfo(
    val packageName: String,
    /** Human label from the APK. Never the package id; the source falls back to a humanised id. */
    val label: String,
    /** ARGB of the letter avatar; null = neutral tile. Unused when [icon] is set. */
    val iconColor: Long?,
    val system: Boolean,
    val enabled: Boolean,
    /** Why this app can't be touched, in user words; null when actions are allowed. */
    val protectedReason: String?,
    val version: String,
    /** The app's own icon as a PNG, from the TV; null shows the letter avatar. */
    val icon: ByteArray? = null,
    /** Installed by the user (`pm list packages -3`), so Uninstall is offered. Never for apps that came with the TV. */
    val removable: Boolean = false,
)

data class Socket(val app: String, val remote: String, val state: String)
data class AppTraffic(val app: String, val rxMb: Int, val txMb: Int)

data class NetworkInfo(
    val iface: String,
    val ip: String,
    val mac: String?,
    val ssid: String?,
    val rssi: Int?,
    val linkMbps: Int?,
    val sockets: List<Socket>,
    val traffic: List<AppTraffic>,
)

/**
 * [Change] sets one [app.tellyscope.adb.Tweak]: its entry's packageName is the tweak's name and
 * [HistoryEntry.previous] holds what Undo writes back. [ClearCaches] has no undo.
 */
enum class Action { Stop, Disable, Enable, Open, Uninstall, Change, ClearCaches }

data class HistoryEntry(
    val id: Long,
    val deviceId: String,
    val at: Long,
    val action: Action,
    val packageName: String,
    val label: String,
    /** What the undo button runs; null when there is nothing to undo. */
    val undo: Action?,
    val undoneAt: Long?,
    /** For [Action.Change]: the settings as they were before, null for a key that wasn't set. */
    val previous: Map<String, String?>? = null,
)

/** The TV-wide settings shown on its page; null for one the TV doesn't report. */
data class TvTweaks(
    /** The Private DNS provider, when one is set and in use; null means Automatic or Off. */
    val dnsHost: String?,
    /** Window animation scale, e.g. "1.0"; null when unset (Android's 1×). */
    val animationScale: String?,
    /** Private DNS came in Android 9: older TVs accept the setting and ignore it. */
    val dnsSupported: Boolean = true,
)
