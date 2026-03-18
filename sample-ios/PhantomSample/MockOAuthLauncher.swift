import Foundation
import PhantomConnectSDK

/// OAuth launcher for E2E testing that bypasses ASWebAuthenticationSession.
///
/// Mirrors the Android MockOAuthLauncher: hits the mock server's login endpoint
/// to verify it's running, then returns hardcoded mock redirect params.
class MockOAuthLauncher: OAuthLauncher {

    func launch(url: String, callbackScheme: String) async throws -> OAuthResult {
        // Verify the mock server is responding
        guard let requestUrl = URL(string: url) else {
            return OAuthResult.Error(cause: KotlinThrowable(message: "Invalid URL: \(url)"))
        }

        do {
            let (_, response) = try await URLSession.shared.data(from: requestUrl)
            let httpResponse = response as! HTTPURLResponse
            guard httpResponse.statusCode == 200 else {
                return OAuthResult.Error(
                    cause: KotlinThrowable(message: "Mock server returned \(httpResponse.statusCode)")
                )
            }
        } catch {
            return OAuthResult.Error(cause: KotlinThrowable(message: error.localizedDescription))
        }

        // Return mock params matching the Android MockOAuthLauncher
        return OAuthResult.Success(params: [
            "wallet_id": "mock-wallet-123",
            "organization_id": "mock-org-456",
            "selected_account_index": "0",
            "expires_in_ms": "604800000",
            "auth_user_id": "mock-user-789",
        ])
    }
}
