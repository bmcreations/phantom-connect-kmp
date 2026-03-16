package dev.bmcreations.phantom.connect

/**
 * Platform-specific OAuth launcher. Implementations open a secure browser
 * for the Phantom login flow and return the redirect result.
 *
 * Provided implementations:
 * - Android: `AndroidOAuthLauncher` (Custom Tabs)
 * - iOS: `IosOAuthLauncher` (ASWebAuthenticationSession)
 */
interface OAuthLauncher {
    /** Launch the OAuth flow at [url] and suspend until callback arrives on [callbackScheme]. */
    suspend fun launch(url: String, callbackScheme: String): OAuthResult
}

sealed class OAuthResult {
    /** Redirect received with parsed query parameters. */
    data class Success(val params: Map<String, String>) : OAuthResult()
    data class Cancelled(val reason: String? = null) : OAuthResult()
    data class Error(val cause: Throwable) : OAuthResult()
}
