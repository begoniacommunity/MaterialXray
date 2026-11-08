package com.material.xray.core.model

import java.security.SecureRandom
import java.util.Random

/**
 * The local proxy the core opens alongside, or instead of, the device tunnel: one port that speaks
 * both SOCKS5 and HTTP, always behind [username] and [password].
 *
 * Every app on the device can reach a loopback port, so the credentials are what keeps other apps
 * from using the proxy, or from learning the exit address through it, without being given them.
 */
data class LocalProxySettings(
    val port: Int,
    val username: String,
    val password: String,
    val allowLan: Boolean,
) {
    /** The address the core listens on. */
    val listenAddress: String
        get() = if (allowLan) ANY_ADDRESS else LOOPBACK_ADDRESS

    /** A proxy URI such as `socks5://user:pass@127.0.0.1:10808`. The credentials are alphanumeric, so nothing needs escaping. */
    fun connectionUri(scheme: String, host: String): String = "$scheme://$username:$password@$host:$port"

    companion object {
        const val DEFAULT_PORT = 10808
        const val MIN_PORT = 1024
        const val MAX_PORT = 65535
        const val CREDENTIAL_LENGTH = 8
        const val MAX_CREDENTIAL_LENGTH = 32
        const val LOOPBACK_ADDRESS = "127.0.0.1"
        const val ANY_ADDRESS = "0.0.0.0"
        const val SOCKS_SCHEME = "socks5"

        fun isValidPort(port: Int): Boolean = port in MIN_PORT..MAX_PORT

        /** Letters and digits only, so a credential goes into a proxy URI unescaped. */
        fun isValidCredential(value: String): Boolean = value.length in 1..MAX_CREDENTIAL_LENGTH &&
            value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }

        fun normalizePort(port: Int?): Int = port?.takeIf(::isValidPort) ?: DEFAULT_PORT

        private const val CREDENTIAL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        fun generateCredential(random: Random = SecureRandom()): String = buildString(CREDENTIAL_LENGTH) {
            repeat(CREDENTIAL_LENGTH) { append(CREDENTIAL_ALPHABET[random.nextInt(CREDENTIAL_ALPHABET.length)]) }
        }
    }
}
