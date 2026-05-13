package dev.bmcreations.phantom.connect

import dev.bmcreations.phantom.connect.internal.auth.Auth2Token
import dev.bmcreations.phantom.connect.internal.crypto.toBase64Url
import kotlin.test.*

class Auth2TokenTest {

    private fun buildFakeJwt(payload: String): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val headerB64 = header.encodeToByteArray().toBase64Url()
        val payloadB64 = payload.encodeToByteArray().toBase64Url()
        val sigB64 = "fake-signature".encodeToByteArray().toBase64Url()
        return "$headerB64.$payloadB64.$sigB64"
    }

    @Test
    fun decodesValidAccessToken() {
        val payload = """{"sub":"user-123","exp":1700000000,"ext":{"a2t":"oidc-token-abc"}}"""
        val jwt = buildFakeJwt(payload)

        val decoded = Auth2Token.decode(jwt)

        assertEquals("user-123", decoded.userId)
        assertEquals("oidc-token-abc", decoded.auth2Token)
        assertEquals(1700000000L, decoded.expiresAt)
    }

    @Test
    fun throwsOnInvalidJwtFormat() {
        assertFailsWith<IllegalArgumentException> {
            Auth2Token.decode("not-a-jwt")
        }
    }

    @Test
    fun throwsOnMissingSub() {
        val payload = """{"exp":1700000000,"ext":{"a2t":"token"}}"""
        val jwt = buildFakeJwt(payload)
        assertFailsWith<IllegalArgumentException> {
            Auth2Token.decode(jwt)
        }
    }

    @Test
    fun throwsOnMissingExt() {
        val payload = """{"sub":"user","exp":1700000000}"""
        val jwt = buildFakeJwt(payload)
        assertFailsWith<IllegalArgumentException> {
            Auth2Token.decode(jwt)
        }
    }

    @Test
    fun throwsOnMissingA2t() {
        val payload = """{"sub":"user","exp":1700000000,"ext":{}}"""
        val jwt = buildFakeJwt(payload)
        assertFailsWith<IllegalArgumentException> {
            Auth2Token.decode(jwt)
        }
    }

    @Test
    fun needsRefreshReturnsTrueWhenExpired() {
        assertTrue(Auth2Token.needsRefresh(expiresAt = 1000, nowSeconds = 1001))
    }

    @Test
    fun needsRefreshReturnsTrueWithinBuffer() {
        // Expires at 2000, buffer is 900 (15 min), so refresh needed at 1100+
        assertTrue(Auth2Token.needsRefresh(expiresAt = 2000, nowSeconds = 1100))
        assertTrue(Auth2Token.needsRefresh(expiresAt = 2000, nowSeconds = 1101))
    }

    @Test
    fun needsRefreshReturnsFalseWhenFresh() {
        // Expires at 2000, buffer is 900, so 1099 is still fresh
        assertFalse(Auth2Token.needsRefresh(expiresAt = 2000, nowSeconds = 1099))
    }

    @Test
    fun needsRefreshWithCustomBuffer() {
        assertFalse(Auth2Token.needsRefresh(expiresAt = 2000, nowSeconds = 1899, bufferSeconds = 100))
        assertTrue(Auth2Token.needsRefresh(expiresAt = 2000, nowSeconds = 1901, bufferSeconds = 100))
    }
}
