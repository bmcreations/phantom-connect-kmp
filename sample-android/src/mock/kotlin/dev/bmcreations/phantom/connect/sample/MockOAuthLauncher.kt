package dev.bmcreations.phantom.connect.sample

import dev.bmcreations.phantom.connect.OAuthLauncher
import dev.bmcreations.phantom.connect.OAuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * OAuth launcher for E2E testing that bypasses Chrome entirely.
 *
 * Instead of opening Custom Tabs, it makes an HTTP request to the mock server's
 * login endpoint and returns hardcoded mock redirect params.
 *
 * This tests the full SDK pipeline (key gen → stamp → API calls → session)
 * without depending on browser state (Chrome first-run, etc.).
 */
class MockOAuthLauncher : OAuthLauncher {

    override suspend fun launch(url: String, callbackScheme: String): OAuthResult {
        return withContext(Dispatchers.IO) {
            try {
                // Fetch the mock login page to verify the mock server is responding
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val responseCode = connection.responseCode
                connection.disconnect()

                if (responseCode != 200) {
                    return@withContext OAuthResult.Error(
                        IllegalStateException("Mock server returned $responseCode for login page")
                    )
                }

                // Return the mock params that the mock login page would redirect with
                OAuthResult.Success(
                    mapOf(
                        "wallet_id" to "mock-wallet-123",
                        "organization_id" to "mock-org-456",
                        "selected_account_index" to "0",
                        "expires_in_ms" to "604800000",
                        "auth_user_id" to "mock-user-789",
                    )
                )
            } catch (e: Exception) {
                OAuthResult.Error(e)
            }
        }
    }
}
