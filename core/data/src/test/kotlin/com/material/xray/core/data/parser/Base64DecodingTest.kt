package com.material.xray.core.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Base64DecodingTest {
    @Test
    fun `decodes standard and url-safe alphabets with or without padding`() {
        assertEquals("subjects?", decodeLenientBase64ToUtf8("c3ViamVjdHM/"))
        assertEquals("subjects?", decodeLenientBase64ToUtf8("c3ViamVjdHM_"))
        assertEquals("ab", decodeLenientBase64ToUtf8("YWI="))
        assertEquals("ab", decodeLenientBase64ToUtf8("YWI"))
    }

    @Test
    fun `ignores whitespace and line breaks`() {
        assertEquals("hello world", decodeLenientBase64ToUtf8("  aGVsbG8g\r\nd29y\tbGQ=\n"))
    }

    @Test
    fun `rejects text outside the base64 alphabet`() {
        assertNull(decodeLenientBase64("vless://uuid@example.com:443#Node"))
        assertNull(decodeLenientBase64("{\"outbounds\": []}"))
        assertNull(decodeLenientBase64(" \n\t "))
    }
}
