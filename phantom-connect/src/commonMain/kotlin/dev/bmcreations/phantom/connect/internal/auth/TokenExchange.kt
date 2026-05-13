package dev.bmcreations.phantom.connect.internal.auth

import dev.bmcreations.phantom.connect.internal.platform.SdkLogger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "TokenExchange"

@Serializable
internal data class TokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val token_type: String = "Bearer",
    val expires_in: Long = 3600,
)

/**
 * OAuth2 token exchange client.
 * Handles authorization code exchange and token refresh.
 */
internal class TokenExchange(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Exchange an authorization code for tokens.
     *
     * @param authApiBaseUrl Auth server base URL (e.g. "https://auth.phantom.app")
     * @param clientId App ID
     * @param redirectUri OAuth callback URI (must match the one used in the JAR)
     * @param code Authorization code from the OAuth callback
     * @param codeVerifier PKCE code verifier
     * @return Token response with access_token, refresh_token, etc.
     */
    suspend fun exchangeAuthCode(
        authApiBaseUrl: String,
        clientId: String,
        redirectUri: String,
        code: String,
        codeVerifier: String,
    ): TokenResponse {
        val tokenUrl = "$authApiBaseUrl/oauth2/token"
        SdkLogger.debug(TAG, "Exchanging auth code at $tokenUrl")

        val response = httpClient.submitForm(
            url = tokenUrl,
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("client_id", clientId)
                append("redirect_uri", redirectUri)
                append("code", code)
                append("code_verifier", codeVerifier)
            },
        )

        val responseBody = response.bodyAsText()
        SdkLogger.debug(TAG, "Token exchange response (${response.status.value}): $responseBody")

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Token exchange failed (${response.status.value}): $responseBody")
        }

        return json.decodeFromString(TokenResponse.serializer(), responseBody)
    }

    /**
     * Refresh an access token using a refresh token.
     *
     * @param authApiBaseUrl Auth server base URL
     * @param clientId App ID
     * @param redirectUri OAuth callback URI
     * @param refreshToken The refresh token from a previous token response
     * @return Fresh token response
     */
    suspend fun refreshToken(
        authApiBaseUrl: String,
        clientId: String,
        redirectUri: String,
        refreshToken: String,
    ): TokenResponse {
        val tokenUrl = "$authApiBaseUrl/oauth2/token"
        SdkLogger.debug(TAG, "Refreshing token at $tokenUrl")

        val response = httpClient.submitForm(
            url = tokenUrl,
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("client_id", clientId)
                append("refresh_token", refreshToken)
                append("redirect_uri", redirectUri)
            },
        )

        val responseBody = response.bodyAsText()
        SdkLogger.debug(TAG, "Token refresh response (${response.status.value}): $responseBody")

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Token refresh failed (${response.status.value}): $responseBody")
        }

        return json.decodeFromString(TokenResponse.serializer(), responseBody)
    }
}
