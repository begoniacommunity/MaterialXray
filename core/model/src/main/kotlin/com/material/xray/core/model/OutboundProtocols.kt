package com.material.xray.core.model

/**
 * Outbound protocols that never reach a proxy server, compared lowercase. Xray also accepts
 * `direct` and `block` for `freedom` and `blackhole`, so provider configs use either spelling.
 */
val NON_PROXY_OUTBOUND_PROTOCOLS = setOf("freedom", "direct", "blackhole", "block", "dns", "loopback")
