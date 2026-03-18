package dev.bmcreations.phantom.connect.wallet

/**
 * Platform-specific deeplink launcher. Implementations open the Phantom wallet app
 * and return the result from the callback URL.
 *
 * Mirrors the [OAuthLauncher][dev.bmcreations.phantom.connect.OAuthLauncher] pattern
 * from the core SDK.
 */
interface DeeplinkLauncher {
    /** Launch a deeplink [url] and suspend until callback arrives on [callbackScheme]. */
    suspend fun launch(url: String, callbackScheme: String): DeeplinkResult

    /** Check if the Phantom wallet app is installed. */
    suspend fun isAppInstalled(): Boolean

    /**
     * Signal the start of a multi-hop ceremony (connect + stamp round-trips).
     * Platforms use this to keep a transparent overlay alive so the host app
     * never flashes between consecutive deeplink hops.
     */
    fun beginCeremony() {}

    /**
     * Signal the end of the ceremony. Dismisses any overlay kept alive by [beginCeremony].
     */
    fun endCeremony() {}
}

sealed class DeeplinkResult {
    /** Redirect received with parsed query parameters. */
    data class Success(val params: Map<String, String>) : DeeplinkResult()
    data class Cancelled(val reason: String? = null) : DeeplinkResult()
    data class Error(val cause: Throwable) : DeeplinkResult()
}
