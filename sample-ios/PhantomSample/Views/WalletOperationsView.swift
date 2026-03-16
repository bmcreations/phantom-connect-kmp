import SwiftUI
import PhantomConnectSDK

struct WalletOperationsView: View {
    let phantom: PhantomClient
    let session: PhantomWalletSession
    let onBack: () -> Void
    let onDisconnect: () -> Void

    @State private var messageText = "Hello from Phantom SDK!"
    @State private var signatureResult: String?
    @State private var signatureError: String?
    @State private var txResult: String?
    @State private var txError: String?
    @State private var signing = false
    @State private var sendingTx = false
    @State private var showSendConfirmation = false

    @State private var balanceSol: Double?
    @State private var balanceLoading = true
    @State private var balanceError: String?

    private var solAddress: String? {
        session.addresses.first { $0.chain.lowercased() == "solana" }?.address
    }

    private var hasBalance: Bool {
        (balanceSol ?? 0) > 0
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    // Wallet Information
                    SectionCard {
                        Text("Wallet Information")
                            .font(.headline)
                        Spacer().frame(height: 8)
                        WalletInfoSection(session: session)
                    }

                    // SOL Balance
                    SectionCard {
                        Text("SOL Balance")
                            .font(.headline)
                        Spacer().frame(height: 8)
                        HStack {
                            Text(balanceText)
                                .font(.title2)
                                .fontWeight(.bold)
                                .monospaced()
                                .foregroundColor(PhantomColors.blue)
                            Spacer()
                            Button("Refresh") {
                                fetchBalance()
                            }
                            .buttonStyle(.bordered)
                        }
                        if let balanceError {
                            Text(balanceError)
                                .font(.caption)
                                .foregroundColor(PhantomColors.red)
                                .padding(.top, 4)
                        }
                    }

                    // Sign Message
                    SectionCard {
                        Text("Sign Message")
                            .font(.headline)
                        Spacer().frame(height: 4)
                        Text("Enter a message to sign with your wallet:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer().frame(height: 8)
                        TextField("Message", text: $messageText)
                            .textFieldStyle(.roundedBorder)
                        Spacer().frame(height: 8)
                        Button {
                            signMessage()
                        } label: {
                            Text(signing ? "Signing..." : "Sign Message")
                                .fontWeight(.semibold)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(signing || messageText.isEmpty ? Color.gray : PhantomColors.purple)
                                .foregroundColor(.white)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                        .disabled(signing || messageText.isEmpty)
                        ResultDisplay(result: signatureResult, error: signatureError, label: "Signature")
                    }

                    // Sign Transaction
                    SectionCard {
                        Text("Sign Transaction")
                            .font(.headline)
                        Spacer().frame(height: 4)
                        Text("This demonstrates transaction signing capabilities:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer().frame(height: 8)
                        Button {
                            showSendConfirmation = true
                        } label: {
                            Text(signTransactionButtonText)
                                .fontWeight(.semibold)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(sendingTx || !hasBalance ? Color(.systemGray) : PhantomColors.indigo, lineWidth: 1)
                                )
                                .foregroundColor(sendingTx || !hasBalance ? Color(.systemGray) : PhantomColors.indigo)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                        .disabled(sendingTx || !hasBalance)
                        ResultDisplay(result: txResult, error: txError, label: "Transaction", solscanHash: txResult)
                    }

                    // Wallet Actions
                    SectionCard {
                        Text("Wallet Actions")
                            .font(.headline)
                        Spacer().frame(height: 12)
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
                }
                .padding(16)
            }
            .background(Color(.secondarySystemBackground))
            .navigationTitle("Wallet Operations")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(PhantomColors.purple, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        onBack()
                    } label: {
                        Image(systemName: "chevron.left")
                            .foregroundColor(.white)
                    }
                }
            }
            .alert("Send Transaction", isPresented: $showSendConfirmation) {
                Button("Send Transaction") {
                    signTransaction()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This will create a small self-transfer transaction (0.000001 SOL) to demonstrate signing and sending.")
            }
            .task {
                fetchBalance()
            }
        }
    }

    // MARK: - Computed

    private var balanceText: String {
        if balanceLoading { return "Loading..." }
        if balanceError != nil { return "Error" }
        if let balanceSol { return String(format: "%.4f SOL", balanceSol) }
        return "--"
    }

    private var signTransactionButtonText: String {
        if sendingTx { return "Sending..." }
        if !hasBalance { return "Insufficient Balance" }
        return "Demo Transaction Signing"
    }

    // MARK: - Actions

    private func fetchBalance() {
        guard let solAddress else {
            balanceLoading = false
            balanceError = "No Solana address found in session"
            return
        }
        balanceLoading = true
        balanceError = nil
        Task {
            do {
                let lamports = try await SolanaRPC.getBalance(address: solAddress)
                balanceSol = Double(lamports) / 1_000_000_000.0
            } catch {
                balanceError = error.localizedDescription
            }
            balanceLoading = false
        }
    }

    private func signMessage() {
        signing = true
        signatureResult = nil
        signatureError = nil
        Task {
            do {
                signatureResult = try await phantom.solana.signMessage(messageText)
            } catch {
                signatureError = error.localizedDescription
            }
            signing = false
        }
    }

    private func signTransaction() {
        guard let solAddress else { return }
        sendingTx = true
        txResult = nil
        txError = nil
        Task {
            do {
                let tx = try await SolanaRPC.buildSelfTransferTransaction(address: solAddress)
                txResult = try await phantom.solana.signAndSendTransaction(base64Transaction: tx)
                // Refresh balance after successful transaction
                try? await Task.sleep(nanoseconds: 2_000_000_000)
                fetchBalance()
            } catch {
                txError = error.localizedDescription
            }
            sendingTx = false
        }
    }
}
