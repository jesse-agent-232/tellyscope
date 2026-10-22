// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope.data

import app.tellyscope.adb.ApkInfo
import app.tellyscope.adb.InstallResult
import app.tellyscope.adb.InstalledVersion
import app.tellyscope.adb.Progress
import app.tellyscope.adb.TvFile
import app.tellyscope.adb.TvFolder
import kotlinx.coroutines.flow.Flow
import okio.Sink
import okio.Source
import java.io.File

/**
 * Everything the screens need from a TV. The UI only talks to this; an
 * adapter over the `:adb` module implements it for real devices, and
 * [FakeDeviceSource] stands in for screenshots and until that lands. Refresh tiers follow the slice-1 data list:
 * fast 2 s ([live]), medium 10 s ([network]), slow / on open ([apps]).
 */
interface DeviceSource {
    /** LAN sweep for 5555 plus mDNS for wireless debugging. Emits hits as they answer, then [ScanEvent.Done]. */
    fun scan(): Flow<ScanEvent>

    /** Classic connect. Suspends while the TV shows the Allow prompt; the caller owns the timeout. */
    suspend fun connect(hit: ScanHit): ConnectResult

    /** Wireless-debugging pairing with the 6-digit code shown on the TV, then connect. */
    suspend fun pair(hit: ScanHit, code: String): ConnectResult

    /** Previously paired devices, including offline ones. */
    suspend fun known(): List<Device>

    /** Try a known TV again after it stopped at "not allowed": the TV shows its Allow prompt. */
    suspend fun reconnect(deviceId: String)

    /** The user's own name for a TV; blank goes back to the network name. Kept per [historyKey]. */
    suspend fun rename(deviceId: String, name: String?)

    /** Stop reconnecting to a TV and drop it from the remembered list. */
    suspend fun forget(deviceId: String)

    /** Fast-tier stats. Completes when the device goes offline. */
    fun live(deviceId: String): Flow<Snapshot>

    suspend fun apps(deviceId: String): List<AppInfo>

    suspend fun network(deviceId: String): NetworkInfo

    /**
     * Runs an action and records it. Never uninstalls: there is no uninstall in [Action] on
     * purpose. [undoing] is the history entry this reverses, which gets marked undone.
     */
    suspend fun act(deviceId: String, action: Action, packageName: String, undoing: Long? = null): HistoryEntry

    /** Everything Tellyscope changed on this TV, newest first. Survives app restarts. */
    suspend fun history(deviceId: String): List<HistoryEntry>

    /** Turns back on every app Tellyscope disabled, and nothing that was off before. */
    suspend fun restoreAll(deviceId: String): List<HistoryEntry>

    /** The version of [packageName] on the TV, or null when it isn't installed. */
    suspend fun installed(deviceId: String, packageName: String): InstalledVersion?

    /** Installs [apks] (base first). Refuses a downgrade unless [allowDowngrade]; refusals come back, never throw. */
    suspend fun install(
        deviceId: String,
        apks: List<File>,
        apk: ApkInfo,
        allowDowngrade: Boolean,
        onProgress: (Progress) -> Unit,
    ): InstallResult

    /** Files in one of the shared folders, newest first. */
    suspend fun files(deviceId: String, folder: TvFolder): List<TvFile>

    /** Copies a phone file to the TV without overwriting anything; returns the name it got. */
    suspend fun push(deviceId: String, folder: TvFolder, name: String, size: Long?, source: Source, onProgress: (Progress) -> Unit): String

    suspend fun pull(deviceId: String, folder: TvFolder, file: TvFile, sink: Sink, onProgress: (Progress) -> Unit)
}
