import SwiftUI
import PhantomConnectSDK

@main
struct PhantomSampleApp: App {
    let phantom: PhantomClient

    init() {
        let isMock = ProcessInfo.processInfo.environment["PHANTOM_MOCK"] == "1"

        if isMock {
            phantom = PhantomClient(
                appId: "test-app-id",
                redirectScheme: "phantomsample",
                redirectUri: "phantomsample://phantom-callback",
                baseUrl: "http://localhost:8080",
                loginBaseUrl: "http://localhost:8080",
                oauthLauncher: MockOAuthLauncher()
            )
        } else {
            phantom = PhantomClient(
                appId: "f2f3406a-0ecf-4a20-96e4-18293772da65",
                redirectScheme: "phantomsample",
                redirectUri: "phantomsample://phantom-callback"
            )
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView(phantom: phantom)
        }
    }
}
