import Foundation
import SwiftUI
@_exported import PhantomConnectWalletKMP

/// Swift-idiomatic wrapper around the KMP PhantomSdk.
/// All logic lives in PhantomSdk.kt — this file is a thin bridge.
public class PhantomClient {

    private let sdk: PhantomSdk

    /// Create a PhantomClient instance with production defaults.
    ///
    /// - Parameters:
    ///   - appId: Your Phantom Portal app ID
    ///   - redirectScheme: The URL scheme for OAuth callbacks (e.g., "myapp")
    ///   - redirectUri: The full redirect URI (e.g., "myapp://phantom-callback")
    ///   - network: Chain network environment (default: `.mainnet`)
    ///   - logger: Optional log handler for SDK diagnostics
    public convenience init(
        appId: String,
        redirectScheme: String,
        redirectUri: String,
        connectors: [any WalletConnector] = [],
        network: ChainNetwork = .mainnet,
        persistSession: Bool = true,
        logger: PhantomLogHandler? = nil
    ) {
        self.init(
            appId: appId,
            redirectScheme: redirectScheme,
            redirectUri: redirectUri,
            connectors: connectors,
            baseUrl: "https://api.phantom.app",
            loginBaseUrl: "https://connect.phantom.app",
            network: network,
            persistSession: persistSession,
            logger: logger,
            oauthLauncher: nil
        )
    }

    /// Create a PhantomClient with custom URLs and/or OAuth launcher (for testing).
    public init(
        appId: String,
        redirectScheme: String,
        redirectUri: String,
        connectors: [any WalletConnector] = [],
        baseUrl: String,
        loginBaseUrl: String,
        network: ChainNetwork = .mainnet,
        persistSession: Bool = true,
        logger: PhantomLogHandler? = nil,
        oauthLauncher: (any OAuthLauncher)? = nil
    ) {
        let kmpLogger: PhantomLogger? = logger.map { handler in
            PhantomLoggerImpl(handler: handler)
        }
        let config = PhantomSdkConfig(
            appId: appId,
            redirectScheme: redirectScheme,
            redirectUri: redirectUri,
            baseUrl: baseUrl,
            loginBaseUrl: loginBaseUrl,
            providers: [AuthProviderGoogle.shared, AuthProviderApple.shared],
            chains: [ChainSolana.shared],
            network: network.kmp,
            persistSession: persistSession,
            logger: kmpLogger,
            sdkVersion: "0.1.0"
        )
        let launcher = oauthLauncher ?? IosOAuthLauncher()
        sdk = PhantomSdk.companion.create(config: config, oauthLauncher: launcher, connectors: connectors)
    }

    // MARK: - Theme

    /// Set the connect sheet theme.
    public var theme: PhantomConnectTheme {
        get {
            if sdk.theme is ConnectSheetTheme.Dark {
                return .dark
            } else if sdk.theme is ConnectSheetTheme.Light {
                return .light
            } else {
                return .dark
            }
        }
        set {
            switch newValue {
            case .dark:
                sdk.theme = ConnectSheetTheme.Dark.shared
            case .light:
                sdk.theme = ConnectSheetTheme.Light.shared
            case .custom(let bg, let opt, let accent, let primary, let secondary):
                sdk.theme = ConnectSheetTheme.Custom(
                    sheetBackground: Int64(bg),
                    optionBackground: Int64(opt),
                    accentColor: Int64(accent),
                    textPrimary: Int64(primary),
                    textSecondary: Int64(secondary)
                )
            }
        }
    }

    // MARK: - Connect

    /// Show the connect modal and let the user choose a sign-in method.
    /// Handles the full flow (Google, Apple, or wallet creation) automatically.
    public func connect() async -> PhantomClientResult {
        do {
            let result = try await sdk.connectWithSheet()
            return mapConnectResult(result)
        } catch {
            return .error(error)
        }
    }

    /// Connect with a specific provider directly (bypasses the connect modal).
    public func connect(provider: PhantomAuthProvider) async -> PhantomClientResult {
        do {
            let kmpProvider: any AuthProvider = switch provider {
            case .google: AuthProviderGoogle.shared
            case .apple: AuthProviderApple.shared
            }
            let result = try await sdk.connect(provider: kmpProvider)
            return mapConnectResult(result)
        } catch {
            return .error(error)
        }
    }

    /// Connect with a wallet connector directly (bypasses the connect sheet).
    public func connect(connector: any WalletConnector) async -> PhantomClientResult {
        do {
            let result = try await sdk.connectWithWallet(connector: connector)
            return mapConnectResult(result)
        } catch {
            return .error(error)
        }
    }

    /// Create a programmatic app wallet (no OAuth, no browser).
    public func createAppWallet() async -> PhantomClientResult {
        do {
            let result = try await sdk.createAppWallet()
            return mapConnectResult(result)
        } catch {
            return .error(error)
        }
    }

    // MARK: - Session

    /// Get the current session, auto-renewing the authenticator if needed.
    public func getSession() async -> PhantomWalletSession? {
        do {
            guard let session = try await sdk.getSession() else { return nil }
            return PhantomWalletSession(from: session)
        } catch {
            return nil
        }
    }

    /// Get an address for a specific chain from the current session.
    public func getAddress(chain: PhantomChain) async -> String? {
        do {
            return try await sdk.getAddress(chain: chain.kmp)
        } catch {
            return nil
        }
    }

    /// Get the Solana address from the current session.
    public func getSolanaAddress() async -> String? {
        await getAddress(chain: .solana)
    }

    // MARK: - Chain-Scoped Operations

    /// Solana-specific signing operations.
    public var solana: SolanaChain { SolanaChain(sdk: sdk) }

    /// Ethereum-specific signing operations.
    public var ethereum: EthereumChain { EthereumChain(sdk: sdk) }

    // MARK: - Logout

    /// Clear the session and keys.
    public func logout() async {
        do {
            try await sdk.logout()
        } catch {
            // Silently ignore logout errors
        }
    }

    // MARK: - Private

    private func mapConnectResult(_ result: ConnectResult) -> PhantomClientResult {
        switch result {
        case let success as ConnectResult.Success:
            return .success(PhantomWalletSession(from: success.session))
        case let cancelled as ConnectResult.Cancelled:
            return .cancelled(reason: cancelled.reason)
        case let error as ConnectResult.Error:
            return .error(error.cause.asError())
        default:
            return .error(NSError(domain: "PhantomClient", code: -1))
        }
    }
}

// MARK: - Swift Models

/// Result of a connect/createAppWallet operation.
public enum PhantomClientResult {
    case success(PhantomWalletSession)
    case cancelled(reason: String?)
    case error(Error)
}

/// Swift representation of the KMP PhantomSession.
public struct PhantomWalletSession {
    public let walletId: String
    public let organizationId: String
    public let addresses: [WalletAddressInfo]
    public let provider: String
    public let accountDerivationIndex: Int
    public let authUserId: String?
    public let sessionId: String
    public let walletType: PhantomWalletType

    init(from kmp: PhantomSession) {
        self.walletId = kmp.walletId
        self.organizationId = kmp.organizationId
        self.addresses = kmp.addresses.map { addr in
            let wa = addr as! WalletAddress
            return WalletAddressInfo(chain: wa.chainId, address: wa.address, derivationPath: wa.derivationPath)
        }
        self.provider = kmp.providerId
        self.accountDerivationIndex = Int(kmp.accountDerivationIndex)
        self.authUserId = kmp.authUserId
        self.sessionId = kmp.sessionId
        if kmp.walletType is WalletTypeUserWallet {
            self.walletType = .userWallet
        } else if kmp.walletType is WalletTypeDeeplinkWallet {
            self.walletType = .deeplinkWallet
        } else {
            self.walletType = .appWallet
        }
    }
}

public struct WalletAddressInfo {
    public let chain: String
    public let address: String
    public let derivationPath: String
}

public enum PhantomWalletType {
    case userWallet
    case appWallet
    case deeplinkWallet
}

public enum PhantomAuthProvider {
    case google
    case apple
}

public enum PhantomConnectTheme {
    case dark
    case light
    case custom(
        sheetBackground: UInt32,
        optionBackground: UInt32,
        accentColor: UInt32,
        textPrimary: UInt32,
        textSecondary: UInt32
    )
}

public enum PhantomChain {
    case solana
    case ethereum

    var kmp: any Chain {
        switch self {
        case .solana: ChainSolana.shared
        case .ethereum: ChainEthereum.shared
        }
    }
}

public enum ChainNetwork {
    case mainnet
    case devnet
    case testnet

    var kmp: Network {
        switch self {
        case .mainnet: .mainnet
        case .devnet: .devnet
        case .testnet: .testnet
        }
    }
}

/// Callback for receiving SDK log messages.
public typealias PhantomLogHandler = (_ level: String, _ tag: String, _ message: String) -> Void

/// Bridges Swift closure to KMP PhantomLogger interface.
private class PhantomLoggerImpl: PhantomLogger {
    let handler: PhantomLogHandler

    init(handler: @escaping PhantomLogHandler) {
        self.handler = handler
    }

    func log(level: LogLevel, tag: String, message: String) {
        handler(level.name, tag, message)
    }
}

// MARK: - Chain-Scoped Operations

/// Solana-specific signing operations.
public struct SolanaChain {
    fileprivate let sdk: PhantomSdk

    /// Get the Solana address from the current session.
    public func getAddress() async -> String? {
        do {
            return try await sdk.solana.getAddress()
        } catch {
            return nil
        }
    }

    /// Sign a UTF-8 message with the Solana key.
    public func signMessage(_ message: String) async throws -> String {
        return try await sdk.solana.signMessage(message: message)
    }

    /// Sign a transaction without broadcasting.
    public func signTransaction(base64Transaction: String) async throws -> String {
        return try await sdk.solana.signTransaction(transactionBase64: base64Transaction)
    }

    /// Sign and submit a transaction.
    public func signAndSendTransaction(base64Transaction: String) async throws -> String {
        return try await sdk.solana.signAndSendTransaction(transactionBase64: base64Transaction)
    }

    /// Sign multiple transactions in a single KMS call.
    public func signAllTransactions(base64Transactions: [String]) async throws -> [String] {
        let result = try await sdk.solana.signAllTransactions(transactionsBase64: base64Transactions)
        return result.compactMap { $0 as? String }
    }
}

/// Ethereum-specific signing operations.
public struct EthereumChain {
    fileprivate let sdk: PhantomSdk

    /// Get the Ethereum address from the current session.
    public func getAddress() async -> String? {
        do {
            return try await sdk.ethereum.getAddress()
        } catch {
            return nil
        }
    }

    /// EIP-191 personal_sign.
    public func personalSign(message: String) async throws -> String {
        return try await sdk.ethereum.personalSign(message: message)
    }

    /// EIP-712 signTypedData_v4.
    public func signTypedData(typedDataJson: String) async throws -> String {
        return try await sdk.ethereum.signTypedData(typedDataJson: typedDataJson)
    }

    /// Sign a transaction without broadcasting.
    public func signTransaction(base64Transaction: String) async throws -> String {
        return try await sdk.ethereum.signTransaction(transactionBase64: base64Transaction)
    }

    /// Sign and submit a transaction.
    public func signAndSendTransaction(base64Transaction: String) async throws -> String {
        return try await sdk.ethereum.signAndSendTransaction(transactionBase64: base64Transaction)
    }
}

// MARK: - DeeplinkLauncher factory

/// Creates a platform-appropriate ``DeeplinkLauncher`` for iOS.
///
/// This is the Swift-idiomatic equivalent of the Kotlin `createDeeplinkLauncher()` factory.
public func createDeeplinkLauncher() -> any DeeplinkLauncher {
    IosDeeplinkLauncher()
}

// MARK: - IosDeeplinkLauncher Swift helpers

extension IosDeeplinkLauncher {
    /// Call from your `onOpenURL` handler when a deeplink callback is received.
    public static func handleCallback(url: URL) {
        companion.handleCallback(url: url.absoluteString)
    }

    /// Call if the user returns to the app without completing the deeplink flow.
    public static func handleCancellation() {
        companion.handleCancellation()
    }
}
