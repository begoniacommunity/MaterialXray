package com.material.xray.core.data.parser

import java.util.Base64

/**
 * Decodes base64 that may use either the standard or the URL-safe alphabet, with or without
 * padding. Share links and subscription payloads in the wild mix all four combinations, so a
 * parser that accepts only one variant silently rejects valid configurations.
 */
internal fun decodeLenientBase64(value: String): ByteArray? {
    // Both decoders reject any other character anyway. Checking up front means a large plain-text
    // subscription body fails here without being copied several times first.
    if (!value.all { it.isBase64Alphabet() || it.isWhitespace() }) return null
    val sanitized = value.filterNot(Char::isWhitespace)
    if (sanitized.isEmpty()) return null

    val candidates = buildList {
        add(sanitized)
        add(sanitized.padBase64())
    }.distinct()

    for (candidate in candidates) {
        runCatching {
            Base64.getDecoder().decode(candidate)
        }.getOrNull()?.let { return it }

        runCatching {
            Base64.getUrlDecoder().decode(candidate)
        }.getOrNull()?.let { return it }
    }

    return null
}

internal fun decodeLenientBase64ToUtf8(value: String): String? = decodeLenientBase64(value)?.toString(Charsets.UTF_8)

private fun String.padBase64(): String {
    val padding = (4 - (length % 4)) % 4
    return this + "=".repeat(padding)
}

private fun Char.isBase64Alphabet(): Boolean = this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "+/-_="
