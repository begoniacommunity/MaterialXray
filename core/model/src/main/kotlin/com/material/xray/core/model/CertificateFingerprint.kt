package com.material.xray.core.model

/** Xray pins SHA-256 of the complete DER certificate, expressed as hexadecimal. */
fun parseCertificateFingerprints(value: String): List<String> {
    if (value.isBlank()) return emptyList()
    val entries = value.split(',').map(String::trim).filter(String::isNotEmpty)
    require(entries.isNotEmpty()) { "A non-empty fingerprint field must contain a certificate fingerprint" }
    return entries.map { entry ->
        val fingerprint = entry.trim().replace(":", "").lowercase()
        require(fingerprint.length == 64 && fingerprint.all { it in '0'..'9' || it in 'a'..'f' }) {
            "A certificate fingerprint must contain 64 hexadecimal digits"
        }
        fingerprint
    }.distinct()
}
