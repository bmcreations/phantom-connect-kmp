package dev.bmcreations.phantom.connect.fakes

import dev.bmcreations.phantom.connect.OAuthLauncher
import dev.bmcreations.phantom.connect.OAuthResult

internal class FakeOAuthLauncher : OAuthLauncher {
    var nextResult: OAuthResult = OAuthResult.Cancelled("not configured")
    var launchedUrls = mutableListOf<String>()
    var launchCount = 0
        private set

    override suspend fun launch(url: String, callbackScheme: String): OAuthResult {
        launchCount++
        launchedUrls.add(url)
        return nextResult
    }

    /** Configure a successful OAuth redirect with standard Phantom callback params. */
    fun succeedWith(
        walletId: String = "test-wallet-id",
        organizationId: String = "test-org-id",
        selectedAccountIndex: Int = 0,
        expiresInMs: Long = 604800000,
        authUserId: String = "test-auth-user-id",
    ) {
        nextResult = OAuthResult.Success(
            params = mapOf(
                "wallet_id" to walletId,
                "organization_id" to organizationId,
                "selected_account_index" to selectedAccountIndex.toString(),
                "expires_in_ms" to expiresInMs.toString(),
                "auth_user_id" to authUserId,
            )
        )
    }
}
