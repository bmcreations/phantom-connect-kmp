import SwiftUI
import PhantomConnectSDK

struct ContentView: View {
    let phantom: PhantomClient
    let walletConnector: PhantomWalletConnector?

    @State private var session: PhantomWalletSession?
    @State private var error: String?
    @State private var screen: Screen = .home

    enum Screen {
        case home, wallet
    }

    var body: some View {
        Group {
            switch screen {
            case .home:
                HomeView(
                    phantom: phantom,
                    walletConnector: walletConnector,
                    session: $session,
                    error: $error,
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
        .task {
            session = await phantom.getSession()
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
