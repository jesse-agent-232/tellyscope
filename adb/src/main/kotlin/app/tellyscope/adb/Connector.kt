// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbCertPolicy
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import com.flyfishxu.kadb.exception.AdbPairAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okio.Buffer
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.channels.InterruptedByTimeoutException

sealed interface ConnectResult {
    class Online(val session: AdbSession) : ConnectResult
    /** Port answered but the TV never accepted our key within the wait. */
    data object NotAllowed : ConnectResult
    /** Wireless debugging port that doesn't know our key yet: pair with the 6-digit code first. */
    data object NeedsPairing : ConnectResult
    data class Unreachable(val reason: String) : ConnectResult
}

/**
 * Opens ADB connections with one persisted host key, so the TV's "always allow" survives restarts.
 * Call [init] once per process before anything else.
 */
object Connector {
    const val ALLOW_WAIT_MS = 60_000
    private const val TCP_TIMEOUT_MS = 3_000

    fun init(keyFile: File) {
        keyFile.parentFile?.mkdirs()
        KadbCert.configure(
            store = OkioFilePrivateKeyStore(privateKeyPath = keyFile.absolutePath.toPath()),
            policy = KadbCertPolicy(),
            additionalPrivateKeysPem = emptyList(),
        )
        KadbCert.ensureReady()
    }

    /**
     * Connects to [host]:[port]. The call blocks while the Allow prompt is on the TV, up to
     * [allowWaitMs]; the UI shows WAITING for that whole time. Cancelling it closes the attempt.
     */
    suspend fun connect(host: String, port: Int, allowWaitMs: Int = ALLOW_WAIT_MS): ConnectResult {
        // Probe TCP first: a refused or silent port is "unreachable", not "not allowed".
        try {
            runInterruptible(Dispatchers.IO) { Socket().use { it.connect(InetSocketAddress(host, port), TCP_TIMEOUT_MS) } }
        } catch (e: IOException) {
            return ConnectResult.Unreachable(e.message ?: e.javaClass.simpleName)
        }
        val kadb = Kadb.create(host, port, connectTimeout = TCP_TIMEOUT_MS, socketTimeout = allowWaitMs)
        return try {
            val out = runInterruptible(Dispatchers.IO) { kadb.shell("echo ok").output }
            if (out.trim() != "ok") throw IOException("unexpected probe output")
            ConnectResult.Online(KadbSession(kadb))
        } catch (e: CancellationException) {
            kadb.close()
            throw e
        } catch (e: AdbPairAuthException) {
            kadb.close()
            ConnectResult.NeedsPairing
        } catch (e: Exception) {
            // Kadb also throws IllegalStateException on protocol surprises; never let it escape.
            kadb.close()
            // No answer times out; Deny makes adbd drop the socket. Both read as "not allowed".
            if (e.isAuthFailure()) ConnectResult.NotAllowed else ConnectResult.Unreachable(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Android 11+ wireless debugging: pair once with the 6-digit code, then [connect] to the connect port. */
    suspend fun pair(host: String, pairingPort: Int, code: String) {
        require(code.length == 6 && code.all(Char::isDigit)) { "pairing code must be 6 digits" }
        Kadb.pair(host, pairingPort, code)
    }

    private fun Throwable.isAuthFailure(): Boolean =
        generateSequence(this) { it.cause }.any {
                it is java.io.EOFException || it is InterruptedIOException || it is InterruptedByTimeoutException
            }
}

internal class KadbSession(private val kadb: Kadb) : AdbSession {
    /**
     * Kadb blocks the calling thread while it waits for the reply; that wait is interruptible, so
     * cancelling one call (a closed screen, a timeout) ends only that stream, not the connection.
     */
    override suspend fun shell(command: String): ShellResult {
        val r = runInterruptible(Dispatchers.IO) { kadb.shell(command) }
        return ShellResult(r.output, r.exitCode)
    }

    override suspend fun push(bytes: ByteArray, remotePath: String) = runInterruptible(Dispatchers.IO) {
        kadb.push(Buffer().write(bytes), remotePath, "644".toInt(8), System.currentTimeMillis())
    }

    override fun install(source: Source, size: Long, vararg options: String) = kadb.install(source, size, *options)

    override fun installMultiple(apks: List<File>, vararg options: String) = kadb.installMultiple(apks, *options)

    override fun push(source: Source, remotePath: String, mode: Int, lastModifiedMs: Long) =
        kadb.push(source, remotePath, mode, lastModifiedMs)

    override fun pull(sink: Sink, remotePath: String) = kadb.pull(sink, remotePath)

    override fun list(remoteDir: String): List<RemoteEntry> = kadb.openSync().use { sync ->
        sync.list(remoteDir).map { RemoteEntry(it.name, it.mode, it.size, it.mtimeSec) }
    }

    override fun close() = kadb.close()
}
