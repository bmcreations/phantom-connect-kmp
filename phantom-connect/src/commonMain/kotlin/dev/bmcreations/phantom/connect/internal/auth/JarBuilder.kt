package dev.bmcreations.phantom.connect.internal.auth

import dev.bmcreations.phantom.connect.internal.crypto.P256Jwk
import dev.bmcreations.phantom.connect.internal.crypto.P256KeyStoreProvider
import dev.bmcreations.phantom.connect.internal.crypto.toBase64Url
import dev.bmcreations.phantom.connect.internal.platform.TimeProvider
import kotlinx.serialization.json.*

/**
 * Builds ES256-signed JWTs (JAR — JWT-Secured Authorization Request) for the auth2 flow.
 *
 * The JAR is sent as a URL fragment to the login page, which uses it to
 * initiate the OAuth2 authorization code flow with PKCE.
 */
internal class JarBuilder(
    private val p256KeyStore: P256KeyStoreProvider,
    private val timeProvider: TimeProvider,
) {
    private val json = Json { encodeDefaults = true }

    /**
     * Build a signed JAR JWT.
     *
     * @param keyTag P-256 key tag to sign with
     * @param aud Audience (auth server URL)
     * @param clientId App ID
     * @param redirectUri OAuth callback URI
     * @param nonce OIDC nonce derived from P-256 public key + salt
     * @param codeChallenge PKCE code challenge (S256)
     * @param loginHint Provider hint (e.g. "google:auth2")
     * @param state Session ID for CSRF protection
     * @param shouldMigrate Whether to migrate from legacy flow
     * @return Signed JWT string: {header}.{payload}.{signature}
     */
    suspend fun buildJar(
        keyTag: String,
        aud: String,
        clientId: String,
        redirectUri: String,
        nonce: String,
        codeChallenge: String,
        loginHint: String,
        state: String,
        shouldMigrate: Boolean = true,
    ): String {
        val jwk = p256KeyStore.getJwk(keyTag)
            ?: throw IllegalStateException("No P-256 key for tag: $keyTag")

        val now = timeProvider.now().epochSeconds
        val exp = now + 300 // 5 minutes

        // Build header
        val header = buildJsonObject {
            put("alg", "ES256")
            put("typ", "oauth-authz-req+jwt")
            putJsonObject("jwk") {
                put("kty", jwk.kty)
                put("crv", jwk.crv)
                put("x", jwk.x)
                put("y", jwk.y)
            }
        }

        // Build payload
        val payload = buildJsonObject {
            put("aud", aud)
            put("iat", now)
            put("exp", exp)
            put("client_id", clientId)
            put("redirect_uri", redirectUri)
            put("scope", "openid offline_access")
            put("nonce", nonce)
            put("code_challenge", codeChallenge)
            put("code_challenge_method", "S256")
            put("login_hint", loginHint)
            put("state", state)
            put("should_migrate", shouldMigrate)
        }

        // Encode header and payload
        val headerB64 = json.encodeToString(JsonObject.serializer(), header)
            .encodeToByteArray().toBase64Url()
        val payloadB64 = json.encodeToString(JsonObject.serializer(), payload)
            .encodeToByteArray().toBase64Url()

        // Sign: ECDSA-SHA256 over "{header}.{payload}"
        val signingInput = "$headerB64.$payloadB64"
        val derSignature = p256KeyStore.sign(keyTag, signingInput.encodeToByteArray())

        // Convert DER signature to raw R||S (64 bytes) for JWS
        val rawSignature = derToRawSignature(derSignature)
        val signatureB64 = rawSignature.toBase64Url()

        return "$headerB64.$payloadB64.$signatureB64"
    }

    /**
     * Convert a DER-encoded ECDSA signature to raw R||S format (64 bytes for P-256).
     *
     * DER format: 0x30 [total-len] 0x02 [r-len] [r-bytes] 0x02 [s-len] [s-bytes]
     * Raw format: R(32 bytes, zero-padded) || S(32 bytes, zero-padded)
     */
    companion object {
        internal fun derToRawSignature(der: ByteArray): ByteArray {
            var offset = 0
            require(der[offset++] == 0x30.toByte()) { "Expected SEQUENCE tag" }
            offset++ // skip total length

            // Read R
            require(der[offset++] == 0x02.toByte()) { "Expected INTEGER tag for R" }
            val rLen = der[offset++].toInt() and 0xFF
            val rBytes = der.copyOfRange(offset, offset + rLen)
            offset += rLen

            // Read S
            require(der[offset++] == 0x02.toByte()) { "Expected INTEGER tag for S" }
            val sLen = der[offset++].toInt() and 0xFF
            val sBytes = der.copyOfRange(offset, offset + sLen)

            // Pad/trim to exactly 32 bytes each
            val r = padOrTrimTo32(rBytes)
            val s = padOrTrimTo32(sBytes)

            return r + s
        }

        private fun padOrTrimTo32(bytes: ByteArray): ByteArray {
            return when {
                bytes.size == 32 -> bytes
                bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size) // trim leading zeros
                else -> ByteArray(32 - bytes.size) + bytes // pad with leading zeros
            }
        }
    }
}
