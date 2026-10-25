package com.material.xray.core.network

import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import org.koin.core.annotation.Singleton

data class TlsCertificateEndpoint(val address: String, val port: Int, val serverName: String, val alpn: List<String> = emptyList()) {
    init {
        require(address.isNotBlank() && serverName.isNotBlank() && port in 1..65535)
        require(alpn.all { it.isNotEmpty() && it.length <= 255 && it.all { character -> character.code in 33..126 } })
    }
}

data class FetchedTlsCertificate(
    val endpoint: TlsCertificateEndpoint,
    val sha256: String,
    val subject: String,
    val issuer: String,
    val validFrom: Long,
    val validUntil: Long,
    val trusted: Boolean,
)

/** Inspects one TLS handshake. Never sends HTTP, proxy credentials, or application data. */
@Singleton
class TlsCertificateFetcher(private val client: OkHttpClient, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
    suspend fun fetch(endpoint: TlsCertificateEndpoint): FetchedTlsCertificate = coroutineScope {
        val socket = Socket()
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { runCatching { socket.close() } }
            launch(ioDispatcher) {
                try {
                    val result = inspect(endpoint, socket)
                    continuation.resumeWith(Result.success(result))
                } catch (error: Exception) {
                    continuation.resumeWith(Result.failure(error))
                } finally {
                    socket.close()
                }
            }
        }
    }

    private fun inspect(endpoint: TlsCertificateEndpoint, socket: Socket): FetchedTlsCertificate {
        val trust = InspectingTrustManager(checkNotNull(client.x509TrustManager))
        // This permissive context belongs only to this inspection; the shared client stays strict.
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), null) }
        socket.connect(InetSocketAddress(endpoint.address.removeSurrounding("[", "]"), endpoint.port), TIMEOUT_MS)
        socket.soTimeout = TIMEOUT_MS
        return (context.socketFactory.createSocket(socket, endpoint.serverName, endpoint.port, true) as SSLSocket).use { tls ->
            tls.soTimeout = TIMEOUT_MS
            tls.sslParameters = tls.sslParameters.apply {
                // SNI is a DNS name; IP literals still undergo hostname verification below.
                if (!endpoint.serverName.contains(':') && !endpoint.serverName.all { it.isDigit() || it == '.' }) {
                    serverNames = listOf(SNIHostName(endpoint.serverName))
                }
            }
            tls.configureAlpn(endpoint.alpn)
            tls.startHandshake()
            val certificate = tls.session.peerCertificates.firstOrNull() as? X509Certificate
                ?: throw SSLException("The peer did not present an X.509 certificate")
            val hostnameMatches = client.hostnameVerifier.verify(endpoint.serverName, tls.session)
            FetchedTlsCertificate(
                endpoint = endpoint,
                sha256 = certificateSha256(certificate),
                subject = certificate.subjectX500Principal.name,
                issuer = certificate.issuerX500Principal.name,
                validFrom = certificate.notBefore.time,
                validUntil = certificate.notAfter.time,
                trusted = trust.trusted && hostnameMatches,
            )
        }
    }
}

internal fun certificateSha256(certificate: X509Certificate): String = MessageDigest.getInstance("SHA-256")
    .digest(certificate.encoded).joinToString("") { "%02x".format(Locale.ROOT, it) }

private class InspectingTrustManager(private val system: X509TrustManager) : X509TrustManager {
    var trusted: Boolean = false
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        trusted = try {
            system.checkServerTrusted(chain, authType)
            chain.first().checkValidity()
            true
        } catch (_: CertificateException) {
            false
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = throw CertificateException("Client authentication is not supported")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private const val TIMEOUT_MS = 10_000

private fun SSLSocket.configureAlpn(protocols: List<String>) {
    if (protocols.isEmpty()) return
    try {
        val parameters = sslParameters
        // Public Java API on JVM / Android 10+, Conscrypt socket API on Android 7–9.
        val setter = parameters.javaClass.methods.firstOrNull { it.name == "setApplicationProtocols" && it.parameterTypes.size == 1 }
        if (setter != null) {
            setter.invoke(parameters, protocols.toTypedArray() as Any)
            sslParameters = parameters
        } else {
            val encoded = protocols.flatMap { listOf(it.length.toByte()) + it.toByteArray(Charsets.US_ASCII).toList() }.toByteArray()
            javaClass.getMethod("setAlpnProtocols", ByteArray::class.java).invoke(this, encoded)
        }
    } catch (error: ReflectiveOperationException) {
        throw SSLException("The platform cannot configure the requested ALPN protocols", error)
    }
}
