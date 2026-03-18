package dev.bmcreations.phantom.connect

import dev.bmcreations.phantom.connect.internal.crypto.fromBase64Url
import dev.bmcreations.phantom.connect.internal.crypto.toBase64Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class Base64UtilsTest {

    @Test
    fun encodeEmptyBytes() {
        assertEquals("", ByteArray(0).toBase64Url())
    }

    @Test
    fun roundTripSimpleBytes() {
        val input = byteArrayOf(0, 1, 2, 3, 4, 5)
        val encoded = input.toBase64Url()
        val decoded = encoded.fromBase64Url()
        assertContentEquals(input, decoded)
    }

    @Test
    fun noPaddingInOutput() {
        // 1 byte -> would normally have "==" padding in base64
        val input = byteArrayOf(42)
        val encoded = input.toBase64Url()
        assertFalse(encoded.contains("="), "Expected no padding, got: $encoded")
    }

    @Test
    fun urlSafeCharacters() {
        // Bytes that produce + and / in standard base64
        val input = byteArrayOf(-1, -2, -3)
        val encoded = input.toBase64Url()
        assertFalse(encoded.contains("+"), "Expected no '+', got: $encoded")
        assertFalse(encoded.contains("/"), "Expected no '/', got: $encoded")
    }

    @Test
    fun roundTrip32ByteKey() {
        val key = ByteArray(32) { it.toByte() }
        val encoded = key.toBase64Url()
        val decoded = encoded.fromBase64Url()
        assertContentEquals(key, decoded)
    }

    @Test
    fun roundTrip64ByteSignature() {
        val sig = ByteArray(64) { (it * 3).toByte() }
        val encoded = sig.toBase64Url()
        val decoded = encoded.fromBase64Url()
        assertContentEquals(sig, decoded)
    }
}
