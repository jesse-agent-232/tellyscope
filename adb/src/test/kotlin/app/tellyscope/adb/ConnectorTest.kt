// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConnectorTest {
    @BeforeTest fun key() = Connector.init(File(Files.createTempDirectory("adbkey").toFile(), "adbkey"))

    @Test fun fingerprintLooksLikeTheTvShowsIt() {
        // The TV prints 16 colon-separated hex pairs; the phone must show the same, and the same each time.
        val f = Connector.fingerprint()
        assertNotNull(f)
        assertTrue(Regex("(?:[0-9A-F]{2}:){15}[0-9A-F]{2}").matches(f), f)
        assertEquals(f, Connector.fingerprint())
    }

    @Test fun adbKeyLayoutIsWhatTheTvHashes() {
        val key = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
            as java.security.interfaces.RSAPublicKey
        val b = java.nio.ByteBuffer.wrap(Connector.adbPublicKey(key)).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(524, b.capacity())
        assertEquals(64, b.getInt(0))
        // n0inv is -1/n mod 2^32, so n0inv * n's lowest word is all ones.
        assertEquals(-1, b.getInt(4) * b.getInt(8))
        assertEquals(65537, b.getInt(520))
    }

    @Test fun portOutOfRangeIsUnreachable() = runBlocking<Unit> {
        // InetSocketAddress threw IllegalArgumentException here, which nothing caught.
        assertIs<ConnectResult.Unreachable>(Connector.connect("127.0.0.1", 70000))
        assertIs<ConnectResult.Unreachable>(Connector.connect("127.0.0.1", 0))
    }

    @Test fun pairingThatDropsMidHandshakeIsUnreachable() = runBlocking<Unit> {
        // A pairing port that hangs up straight away, as one does when the Wi-Fi drops.
        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { server ->
            thread(isDaemon = true) { runCatching { while (true) server.accept().close() } }
            val r = withTimeout(30_000) { Connector.pair("127.0.0.1", server.localPort, "123456") }
            assertIs<PairResult.Unreachable>(r)
        }
    }

    @Test fun pairingWithNothingListeningIsUnreachable() = runBlocking<Unit> {
        val port = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        assertIs<PairResult.Unreachable>(withTimeout(30_000) { Connector.pair("127.0.0.1", port, "123456") })
    }

    @Test fun badCodeIsWrongCode() = runBlocking<Unit> {
        assertEquals(PairResult.WrongCode("pairing code must be 6 digits"), Connector.pair("127.0.0.1", 1, "12a456"))
    }
}
