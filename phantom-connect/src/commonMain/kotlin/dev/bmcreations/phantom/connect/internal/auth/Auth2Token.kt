package dev.bmcreations.phantom.connect.internal.auth

import dev.bmcreations.phantom.connect.internal.crypto.fromBase64Url
import kotlinx.serialization.json.*

/**
 * Thrown when the inner a2t JWT has expired.
 * Upstream auto-disconnects on this error.
 */
class Auth2TokenExpiredError(message: String = "Auth2 token has expired") : Exception(message)

/**
 * Decodes JWT access tokens from the auth2 flow.
 * Extracts the `sub` (user ID), `ext.a2t` (auth2 token), `client_id`,
 * audience claims including wallet identity and wallet tag.
 */
internal object Auth2Token {

    data class WalletIdentity(
        val walletId: String,
        val derivationIndex: Int,
    )

    data class DecodedToken(
        /** User ID from the `sub` claim. */
        val userId: String,
        /** Auth2 token from the `ext.a2t` claim, used for OIDC stamps. */
        val auth2Token: String,
        /** Token expiration time (Unix seconds) from the `exp` claim. */
        val expiresAt: Long,
        /** Client ID from the `client_id` claim. */
        val clientId: String? = null,
        /** Audience array from the `aud` claim. */
        val audience: List<String> = emptyList(),
        /** Wallet identity parsed from `urn:phantom:wallet:` URN in audience. */
        val wallet: WalletIdentity? = null,
        /** Wallet tag parsed from `urn:phantom:wallet-tag:` URN in audience. */
        val walletTag: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Decode a JWT access token and extract auth2-specific claims.
     *
     * @param accessToken The JWT access token string
     * @return Decoded token with userId, auth2Token, expiration, and wallet claims
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

        val clientId = payload["client_id"]?.jsonPrimitive?.content

        val audience = when (val aud = payload["aud"]) {
            is JsonArray -> aud.map { it.jsonPrimitive.content }
            is JsonPrimitive -> listOf(aud.content)
            else -> emptyList()
        }

        val wallet = parseWalletIdentity(audience)
        val walletTag = parseWalletTag(audience)

        return DecodedToken(
            userId = sub,
            auth2Token = a2t,
            expiresAt = exp,
            clientId = clientId,
            audience = audience,
            wallet = wallet,
            walletTag = walletTag,
        )
    }

    /**
     * Get the a2t value from a decoded token, checking inner JWT expiry.
     *
     * @param decoded The decoded token
     * @param nowSeconds Current time in Unix seconds
     * @throws Auth2TokenExpiredError if the inner a2t JWT has expired
     */
    fun getAuth2Token(decoded: DecodedToken, nowSeconds: Long): String {
        // Check if the inner a2t JWT itself has expired
        try {
            val a2tParts = decoded.auth2Token.split(".")
            if (a2tParts.size == 3) {
                val a2tPayloadJson = a2tParts[1].fromBase64Url().decodeToString()
                val a2tPayload = json.decodeFromString(JsonObject.serializer(), a2tPayloadJson)
                val a2tExp = a2tPayload["exp"]?.jsonPrimitive?.long
                if (a2tExp != null && nowSeconds >= a2tExp) {
                    throw Auth2TokenExpiredError("Inner a2t JWT expired at $a2tExp, now $nowSeconds")
                }
            }
        } catch (e: Auth2TokenExpiredError) {
            throw e
        } catch (_: Exception) {
            // If we can't parse the inner JWT, proceed with the token as-is
        }
        return decoded.auth2Token
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

    /**
     * Parse wallet identity from `urn:phantom:wallet:{walletId}:{derivationIndex}` in audience.
     */
    private fun parseWalletIdentity(audience: List<String>): WalletIdentity? {
        val prefix = "urn:phantom:wallet:"
        val walletUrn = audience.firstOrNull { it.startsWith(prefix) } ?: return null
        val parts = walletUrn.removePrefix(prefix).split(":")
        if (parts.size < 2) return null
        val derivationIndex = parts[1].toIntOrNull() ?: return null
        return WalletIdentity(walletId = parts[0], derivationIndex = derivationIndex)
    }

    /**
     * Parse wallet tag from `urn:phantom:wallet-tag:{tag}` in audience.
     */
    private fun parseWalletTag(audience: List<String>): String? {
        val prefix = "urn:phantom:wallet-tag:"
        val tagUrn = audience.firstOrNull { it.startsWith(prefix) } ?: return null
        return tagUrn.removePrefix(prefix)
    }
}
