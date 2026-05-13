package dev.bmcreations.phantom.connect.internal.auth

import dev.bmcreations.phantom.connect.internal.crypto.fromBase64Url
import kotlinx.serialization.json.*

/**
 * Decodes JWT access tokens from the auth2 flow.
 * Extracts the `sub` (user ID) and `ext.a2t` (auth2 token) claims.
 */
internal object Auth2Token {

    data class DecodedToken(
        /** User ID from the `sub` claim. */
        val userId: String,
        /** Auth2 token from the `ext.a2t` claim, used for OIDC stamps. */
        val auth2Token: String,
        /** Token expiration time (Unix seconds) from the `exp` claim. */
        val expiresAt: Long,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Decode a JWT access token and extract auth2-specific claims.
     *
     * @param accessToken The JWT access token string
     * @return Decoded token with userId, auth2Token, and expiration
     * @throws IllegalArgumentException if the token format is invalid
     */
    fun decode(accessToken: String): DecodedToken {
        val parts = accessToken.split(".")
        require(parts.size == 3) { "Invalid JWT format: expected 3 parts, got ${parts.size}" }

        val payloadJson = parts[1].fromBase64Url().decodeToString()
        val payload = json.decodeFromString(JsonObject.serializer(), payloadJson)

        val sub = payload["sub"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'sub' claim in access token")

        val ext = payload["ext"]?.jsonObject
            ?: throw IllegalArgumentException("Missing 'ext' claim in access token")

        val a2t = ext["a2t"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing 'ext.a2t' claim in access token")

        val exp = payload["exp"]?.jsonPrimitive?.long
            ?: throw IllegalArgumentException("Missing 'exp' claim in access token")

        return DecodedToken(
            userId = sub,
            auth2Token = a2t,
            expiresAt = exp,
        )
    }

    /**
     * Check if a token is expired or will expire within the given buffer.
     *
     * @param expiresAt Token expiration (Unix seconds)
     * @param nowSeconds Current time (Unix seconds)
     * @param bufferSeconds Buffer before actual expiry (default: 15 minutes)
     * @return true if the token needs refreshing
     */
    fun needsRefresh(expiresAt: Long, nowSeconds: Long, bufferSeconds: Long = 900): Boolean {
        return nowSeconds >= (expiresAt - bufferSeconds)
    }
}
