import SwiftUI
import PhantomConnectSDK

struct HomeView: View {
    let phantom: PhantomClient
    @Binding var session: PhantomWalletSession?
    @Binding var error: String?
    let onOpenWallet: () -> Void
    let onDisconnect: () -> Void

    @State private var selectedTheme = "Dark"

    var isConnected: Bool { session != nil }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    // Title
                    Text("Phantom Connect SDK")
                        .font(.title2)
                        .fontWeight(.bold)
                    Text("Demo Application")
                        .font(.subheadline)
                        .foregroundColor(.secondary)

                    Spacer().frame(height: 4)

                    // Connection Status
                    SectionCard {
                        Text("Connection Status")
                            .font(.headline)
                        Spacer().frame(height: 8)
                        HStack(spacing: 8) {
                            Circle()
                                .fill(isConnected ? PhantomColors.successGreen : PhantomColors.red)
                                .frame(width: 12, height: 12)
                            Text(isConnected ? "Connected" : "Disconnected")
                                .font(.body)
                        }
                        if let session {
                            Spacer().frame(height: 12)
                            WalletInfoSection(session: session)
                        }
                    }

                    // Modal Theme
                    SectionCard {
                        Text("Modal Theme")
                            .font(.headline)
                        Spacer().frame(height: 4)
                        Text("Switch between different themes for the modal:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer().frame(height: 12)
                        HStack(spacing: 8) {
                            ForEach(["Dark", "Light", "Custom"], id: \.self) { label in
                                Button {
                                    selectedTheme = label
                                    switch label {
                                    case "Light":
                                        phantom.theme = .light
                                    case "Custom":
                                        phantom.theme = .custom(
                                            sheetBackground: 0xFFFF6B35,
                                            optionBackground: 0xFFFF8A5C,
                                            accentColor: 0xFFFFFFFF,
                                            textPrimary: 0xFFFFFFFF,
                                            textSecondary: 0xFFFFD4C2
                                        )
                                    default:
                                        phantom.theme = .dark
                                    }
                                } label: {
                                    Text(label)
                                        .fontWeight(.medium)
                                        .padding(.horizontal, 16)
                                        .padding(.vertical, 8)
                                        .background(
                                            selectedTheme == label
                                                ? PhantomColors.purple
                                                : Color.clear
                                        )
                                        .foregroundColor(
                                            selectedTheme == label
                                                ? .white
                                                : .primary
                                        )
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 8)
                                                .stroke(
                                                    selectedTheme == label
                                                        ? Color.clear
                                                        : Color.secondary.opacity(0.3),
                                                    lineWidth: 1
                                                )
                                        )
                                }
                            }
                        }
                    }

                    if isConnected {
                        // Wallet Actions (connected)
                        SectionCard {
                            Text("Wallet Actions")
                                .font(.headline)
                            Spacer().frame(height: 12)
                            Button {
                                handleConnect()
                            } label: {
                                Text("Open Wallet Modal")
                                    .fontWeight(.semibold)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(PhantomColors.purple)
                                    .foregroundColor(.white)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                            Spacer().frame(height: 8)
                            Button {
                                onOpenWallet()
                            } label: {
                                Text("Open Wallet Operations")
                                    .fontWeight(.semibold)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(PhantomColors.purple)
                                    .foregroundColor(.white)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                            Spacer().frame(height: 8)
                            Button {
                                onDisconnect()
                            } label: {
                                Text("Disconnect Wallet")
                                    .fontWeight(.semibold)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(PhantomColors.red)
                                    .foregroundColor(.white)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                        }
                    } else {
                        // Connect Wallet (disconnected)
                        SectionCard {
                            Text("Connect Wallet")
                                .font(.headline)
                            Spacer().frame(height: 4)
                            Text("Connect your Phantom wallet using various authentication methods:")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Spacer().frame(height: 12)
                            Button {
                                handleConnect()
                            } label: {
                                Text("Open Connect Modal")
                                    .fontWeight(.semibold)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(PhantomColors.purple)
                                    .foregroundColor(.white)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                            Spacer().frame(height: 8)
                            Button {
                                handleConnect(provider: .google)
                            } label: {
                                Text("Connect with Google")
                                    .fontWeight(.semibold)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(PhantomColors.red)
                                    .foregroundColor(.white)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                        }
                    }

                    // Error
                    if let error {
                        SectionCard {
                            Text("Error")
                                .font(.headline)
                            Spacer().frame(height: 8)
                            Text(error)
                                .font(.caption)
                                .foregroundColor(PhantomColors.red)
                                .padding(10)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(PhantomColors.red.opacity(0.1))
                                .clipShape(RoundedRectangle(cornerRadius: 6))
                        }
                    }

                    // About
                    SectionCard {
                        Text("About")
                            .font(.headline)
                        Spacer().frame(height: 8)
                        Text("This demo app showcases the Phantom Connect iOS SDK integration. It demonstrates wallet connection, authentication flows, message signing, and transaction handling.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .padding(16)
            }
            .background(Color(.secondarySystemBackground))
            .navigationTitle("Phantom iOS SDK Demo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(PhantomColors.purple, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .task {
                session = await phantom.getSession()
            }
        }
    }

    private func handleConnect(provider: PhantomAuthProvider? = nil) {
        error = nil
        Task {
            let result: PhantomClientResult
            if let provider {
                result = await phantom.connect(provider: provider)
            } else {
                result = await phantom.connect()
            }
            switch result {
            case .success(let walletSession):
                session = walletSession
            case .cancelled:
                // Re-check session (user may have disconnected via modal)
                session = await phantom.getSession()
            case .error(let err):
                error = err.localizedDescription
            }
        }
    }
}
