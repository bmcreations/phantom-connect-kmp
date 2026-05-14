import SwiftUI
import PhantomConnectSDK

struct ContentView: View {
    @AppStorage("persist_session") private var persistSession = true

    @State private var session: PhantomWalletSession?
    @State private var error: String?
    @State private var screen: Screen = .home
    @State private var sdkInstance: SDKInstance?

    enum Screen {
        case home, wallet
    }

    private var phantom: PhantomClient { sdkInstance!.client }
    private var walletConnector: PhantomWalletConnector? { sdkInstance?.walletConnector }

    var body: some View {
        Group {
            if sdkInstance != nil {
                switch screen {
                case .home:
                    HomeView(
                        phantom: phantom,
                        walletConnector: walletConnector,
                        session: $session,
                        error: $error,
                        persistSession: $persistSession,
                        onOpenWallet: { screen = .wallet },
                        onDisconnect: { handleDisconnect() }
                    )
                case .wallet:
                    if let session {
                        WalletOperationsView(
                            phantom: phantom,
                            session: session,
                            onBack: { screen = .home },
                            onDisconnect: { handleDisconnect() }
                        )
                    }
                }
            }
        }
        .onChange(of: persistSession) { _ in
            rebuildSdk()
        }
        .task {
            if sdkInstance == nil {
                rebuildSdk()
            }
        }
    }

    private func rebuildSdk() {
        let isMock = ProcessInfo.processInfo.environment["PHANTOM_MOCK"] == "1"

        let connector = PhantomWalletConnector(
            deeplinkLauncher: createDeeplinkLauncher(),
            appUrl: "https://phantom-kmp-sample.app",
            callbackScheme: "phantomsample"
        )

        let client: PhantomClient
        if isMock {
            client = PhantomClient(
                appId: "test-app-id",
                redirectScheme: "phantomsample",
                redirectUri: "phantomsample://phantom-auth-callback",
                connectors: [connector],
                baseUrl: "http://localhost:8080",
                loginBaseUrl: "http://localhost:8080",
                persistSession: persistSession,
                oauthLauncher: MockOAuthLauncher()
            )
        } else {
            client = PhantomClient(
                appId: "f2f3406a-0ecf-4a20-96e4-18293772da65",
                redirectScheme: "phantomsample",
                redirectUri: "phantomsample://phantom-auth-callback",
                connectors: [connector],
                persistSession: persistSession
            )
        }

        sdkInstance = SDKInstance(client: client, walletConnector: connector)
        session = nil
        error = nil
        screen = .home

        Task {
            session = await client.getSession()
        }
    }

    private func handleDisconnect() {
        Task {
            await phantom.logout()
            session = nil
            error = nil
            screen = .home
        }
    }
}

private struct SDKInstance {
    let client: PhantomClient
    let walletConnector: PhantomWalletConnector
}
