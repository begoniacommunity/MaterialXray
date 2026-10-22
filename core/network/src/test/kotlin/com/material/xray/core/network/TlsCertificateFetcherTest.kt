package com.material.xray.core.network

import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsCertificateFetcherTest {
    @Test
    fun `self signed certificate is inspected without sending application data or changing client trust`() = runBlocking {
        val certificate = HeldCertificate.Builder().commonName("self-signed").addSubjectAlternativeName("localhost").build()
        val client = OkHttpClient()
        withTlsServer(certificate) { port ->
            val result = TlsCertificateFetcher(client).fetch(TlsCertificateEndpoint("127.0.0.1", port, "localhost"))
            val expected = MessageDigest.getInstance("SHA-256").digest(certificate.certificate.encoded).joinToString("") { "%02x".format(Locale.ROOT, it) }
            assertEquals(expected, result.sha256)
            assertFalse(result.trusted)
            assertTrue(result.subject.contains("self-signed"))
            assertEquals(certificate.certificate.notAfter.time, result.validUntil)
        }
        assertTrue(runCatching { client.x509TrustManager!!.checkServerTrusted(arrayOf(certificate.certificate), "ECDHE_ECDSA") }.isFailure)
    }

    @Test
    fun `trusted chain still requires the configured server name to match`() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val client = OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
        withTlsServer(certificate) { port ->
            assertTrue(TlsCertificateFetcher(client).fetch(TlsCertificateEndpoint("127.0.0.1", port, "localhost")).trusted)
        }
        withTlsServer(certificate) { port ->
            assertFalse(TlsCertificateFetcher(client).fetch(TlsCertificateEndpoint("127.0.0.1", port, "other.example")).trusted)
        }
    }

    @Test
    fun `inspection negotiates the configured ALPN`() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        withTlsServer(certificate, alpn = "h2") { port ->
            val result = TlsCertificateFetcher(OkHttpClient()).fetch(TlsCertificateEndpoint("127.0.0.1", port, "localhost", listOf("h2")))
            assertFalse(result.trusted)
        }
    }

    @Test
    @Suppress("InjectDispatcher") // Real blocking sockets must run off the coroutine test thread.
    fun `cancelling a stalled handshake closes its socket`() = runBlocking {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val peer = async(Dispatchers.IO) { server.accept() }
            val fetch = async(Dispatchers.IO) { TlsCertificateFetcher(OkHttpClient()).fetch(TlsCertificateEndpoint("127.0.0.1", server.localPort, "localhost")) }
            withTimeout(5_000) {
                peer.await().use { accepted ->
                    accepted.soTimeout = 2_000
                    fetch.cancelAndJoin()
                    // Drain the ClientHello, then observe EOF rather than waiting for a socket timeout.
                    val input = accepted.getInputStream()
                    val buffer = ByteArray(4096)
                    while (input.read(buffer) != -1) { /* TLS handshake only */ }
                }
            }
        }
    }

    private suspend fun withTlsServer(certificate: HeldCertificate, alpn: String? = null, block: suspend (Int) -> Unit) {
        val identity = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val executor = Executors.newSingleThreadExecutor()
        try {
            (identity.sslContext().serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket).use { server ->
                val result = executor.submit<Int> {
                    (server.accept() as SSLSocket).use { socket ->
                        socket.soTimeout = 5_000
                        if (alpn != null) socket.sslParameters = socket.sslParameters.apply { applicationProtocols = arrayOf(alpn) }
                        socket.startHandshake()
                        if (alpn != null) assertEquals(alpn, socket.applicationProtocol)
                        socket.inputStream.read()
                    }
                }
                block(server.localPort)
                assertEquals("The certificate probe must not send proxy or HTTP data", -1, result.get(5, TimeUnit.SECONDS))
            }
        } finally {
            executor.shutdownNow()
        }
    }
}
