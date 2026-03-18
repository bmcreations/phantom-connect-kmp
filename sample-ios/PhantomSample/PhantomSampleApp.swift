import SwiftUI
import PhantomConnectSDK

@main
struct PhantomSampleApp: App {
    let phantom: PhantomClient
    let walletConnector: PhantomWalletConnector?

    init() {
        let isMock = ProcessInfo.processInfo.environment["PHANTOM_MOCK"] == "1"

        let connector = PhantomWalletConnector(
            deeplinkLauncher: createDeeplinkLauncher(),
            appUrl: "https://phantom-kmp-sample.app",
            callbackScheme: "phantomsample"
        )
        walletConnector = connector

        if isMock {
            phantom = PhantomClient(
                appId: "test-app-id",
                redirectScheme: "phantomsample",
                redirectUri: "phantomsample://phantom-callback",
                connectors: [connector],
                baseUrl: "http://localhost:8080",
                loginBaseUrl: "http://localhost:8080",
                oauthLauncher: MockOAuthLauncher()
            )
        } else {
            phantom = PhantomClient(
                appId: "f2f3406a-0ecf-4a20-96e4-18293772da65",
                redirectScheme: "phantomsample",
                redirectUri: "phantomsample://phantom-callback",
                connectors: [connector]
            )
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView(phantom: phantom, walletConnector: walletConnector)
                .onOpenURL { url in
                    IosDeeplinkLauncher.handleCallback(url: url)
                }
        }
    }
}
