package dev.bmcreations.phantom.connect.internal.crypto

import dev.bmcreations.phantom.connect.internal.auth.Auth2Token
import dev.bmcreations.phantom.connect.internal.auth.TokenExchange
import dev.bmcreations.phantom.connect.internal.auth.TokenResponse
import dev.bmcreations.phantom.connect.internal.platform.SdkLogger
import dev.bmcreations.phantom.connect.internal.platform.TimeProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "OidcStamper"

@Serializable
internal data class OidcStampPayload(
    val kind: String = "OIDC",
    val idToken: String,
    val publicKey: String,
    val algorithm: String = "secp256r1",
    val salt: String = "",
    val signature: String,
)

/**
 * Signs KMS requests with P-256 and includes an OIDC token.
 *
 * Used for the auth2 flow where the authenticator credential is a P-256 key
 * rather than Ed25519. The stamp includes the OIDC id token (a2t claim from
 * the access token) for server-side verification.
 *
 * Token refresh is handled automatically: if the access token is within
 * 15 minutes of expiry, a refresh is attempted before stamping.
 */
internal class OidcStamper(
    private val p256KeyStore: P256KeyStoreProvider,
    private val keyTag: String = P256KeyStoreTags.ACTIVE,
    private val timeProvider: TimeProvider,
    private val tokenExchange: TokenExchange? = null,
    private val authApiBaseUrl: String = "",
    private val clientId: String = "",
    private val redirectUri: String = "",
    initialAuth2Token: String,
    initialAccessToken: String,
    initialRefreshToken: String? = null,
    initialTokenExpiresAt: Long = 0,
) : Stamper {
    private val stampJson = Json { encodeDefaults = true }

    private var auth2Token: String = initialAuth2Token
    private var accessToken: String = initialAccessToken
    private var refreshToken: String? = initialRefreshToken
    private var tokenExpiresAt: Long = initialTokenExpiresAt

    /** The current bearer token string (e.g. "Bearer eyJ...") for the Authorization header. */
    val bearerToken: String get() = "Bearer $accessToken"

    /** Current refresh token, if available. */
    val currentRefreshToken: String? get() = refreshToken

    /** Token expiration timestamp (Unix seconds). */
    val currentTokenExpiresAt: Long get() = tokenExpiresAt

    /**
     * Construct the X-Phantom-Stamp header value for [bodyBytes].
     *
     * 1. Refresh tokens if needed
     * 2. Sign bodyBytes with P-256 private key
     * 3. Build OIDC stamp JSON
     * 4. Base64url-encode the JSON
     */
    override suspend fun stamp(bodyBytes: ByteArray): String {
        maybeRefreshTokens()

        val publicKey = p256KeyStore.getRawPublicKey(keyTag)
            ?: throw IllegalStateException("No P-256 key for tag: $keyTag")
        val signature = p256KeyStore.sign(keyTag, bodyBytes)

        val stampPayload = OidcStampPayload(
            idToken = auth2Token,
            publicKey = publicKey.toBase64Url(),
            signature = signature.toBase64Url(),
        )

        val jsonStr = stampJson.encodeToString(stampPayload)
        return jsonStr.encodeToByteArray().toBase64Url()
    }

    /**
     * Refresh tokens if within 15-minute buffer of expiry.
     */
    private suspend fun maybeRefreshTokens() {
        val now = timeProvider.now().epochSeconds
        if (!Auth2Token.needsRefresh(tokenExpiresAt, now)) return

        val exchange = tokenExchange ?: return
        val refresh = refreshToken ?: return

        SdkLogger.info(TAG, "Refreshing tokens (expires at $tokenExpiresAt, now $now)")
        try {
            val response = exchange.refreshToken(
                authApiBaseUrl = authApiBaseUrl,
                clientId = clientId,
                redirectUri = redirectUri,
                refreshToken = refresh,
            )
            updateTokens(response)
            SdkLogger.info(TAG, "Token refresh succeeded, new expiry: $tokenExpiresAt")
        } catch (e: Exception) {
            SdkLogger.warn(TAG, "Token refresh failed: ${e.message}")
            // Continue with existing token — it may still work
        }
    }

    private fun updateTokens(response: TokenResponse) {
        accessToken = response.access_token
        if (response.refresh_token != null) {
            refreshToken = response.refresh_token
        }
        val decoded = Auth2Token.decode(response.access_token)
        auth2Token = decoded.auth2Token
        tokenExpiresAt = decoded.expiresAt
    }
}
