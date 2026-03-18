# PhantomConnectSDK (iOS)

Swift wrapper around the Kotlin Multiplatform `PhantomConnectKMP` framework. This package provides a Swift-idiomatic API for [Phantom](https://phantom.app) embedded wallets on iOS, equivalent to the [Phantom Connect React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/index) for native Swift apps.

## Installation

Add this package via Swift Package Manager in Xcode:

1. File > Add Package Dependencies
2. Enter the repository URL (https://github.com/bmcreations/phantom-connect-ios)
3. `import PhantomConnectSDK`

## Quick Start

```swift
import PhantomConnectSDK

// Create the client
let phantom = PhantomClient(
    appId: "your-app-id",
    redirectScheme: "myapp",
    redirectUri: "myapp://phantom-callback"
)

// Restore session on launch (optional -- in SwiftUI)
.task {
    if let session = await phantom.getSession() {
        // update state with restored session
    }
}

// Connect (shows built-in bottom sheet)
let result = await phantom.connect()

// Chain-scoped signing
let sig = try await phantom.solana.signMessage("Hello")
let ethSig = try await phantom.ethereum.personalSign("Hello")

// Logout
await phantom.logout()
```

## Wallet Connector (Phantom App Deeplinks)

Connect and sign via the installed Phantom mobile app using deeplinks. This enables Solana-only wallet operations through the [Phantom deeplink protocol](https://docs.phantom.com/solana/integrating-phantom/deeplinks-solana).

The wallet connector types (`PhantomWalletConnector`, `IosDeeplinkLauncher`) are included in this package -- no separate dependency needed.

### Setup

```swift
import PhantomConnectSDK

let connector = PhantomWalletConnector(
    deeplinkLauncher: createDeeplinkLauncher(),
    appUrl: "https://your-app.example.com",
    callbackScheme: "myapp"
)

let phantom = PhantomClient(
    appId: "your-app-id",
    redirectScheme: "myapp",
    redirectUri: "myapp://phantom-callback",
    connectors: [connector]
)
```

### Handle Callbacks

Register a URL handler so Phantom's deeplink responses reach the SDK:

```swift
@main
struct MyApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    IosDeeplinkLauncher.handleCallback(url: url)
                }
        }
    }
}
```

### Connect via Sheet

When connectors are provided, the connect sheet automatically includes them alongside social login buttons. Each connector's `callToAction` (default: `"Continue with {displayName}"`) is used as the button label:

```swift
let result = await phantom.connect()  // sheet includes wallet option
```

### Connect Directly

Bypass the sheet and connect with the Phantom app directly:

```swift
let result = await phantom.connect(connector: connector)
```

### Parameters

| Parameter          | Description                                                                                                                     |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `deeplinkLauncher` | `createDeeplinkLauncher()` -- handles opening Phantom and receiving callbacks                                                   |
| `appUrl`           | Your app's HTTPS URL, used by Phantom to identify the dapp. Must be a valid URL, not a custom scheme.                           |
| `callbackScheme`   | URL scheme for deeplink callbacks (e.g. `"myapp"`). Phantom sends results back to `{callbackScheme}://phantom-wallet-callback`. |

### Session Persistence

Deeplink wallet sessions persist alongside social login sessions. The connector's crypto state (X25519 keypair, shared secret, session token) is saved with the session. On app restart, `getSession()` restores both the session and the connector state, so signing works without re-connecting.

## API

### `PhantomClient`

| Method                | Description                                                             |
|-----------------------|-------------------------------------------------------------------------|
| `connect()`           | Show connect sheet (includes wallet connectors if provided)             |
| `connect(provider:)`  | Connect with specific social provider                                   |
| `connect(connector:)` | Connect with a wallet connector directly                                |
| `createAppWallet()`   | Create app wallet (no OAuth)                                            |
| `getSession()`        | Get current session (restores saved session, auto-renews authenticator) |
| `logout()`            | Clear session and keys                                                  |

### `phantom.solana` (`SolanaChain`)

| Method                                       | Description        |
|----------------------------------------------|--------------------|
| `getAddress()`                               | Solana address     |
| `signMessage(_:)`                            | Sign UTF-8 message |
| `signTransaction(base64Transaction:)`        | Sign transaction   |
| `signAndSendTransaction(base64Transaction:)` | Sign and submit    |
| `signAllTransactions(base64Transactions:)`   | Batch sign         |

### `phantom.ethereum` (`EthereumChain`)

| Method                                       | Description              |
|----------------------------------------------|--------------------------|
| `getAddress()`                               | Ethereum address         |
| `personalSign(message:)`                     | EIP-191 personal_sign    |
| `signTypedData(typedDataJson:)`              | EIP-712 signTypedData_v4 |
| `signTransaction(base64Transaction:)`        | Sign transaction         |
| `signAndSendTransaction(base64Transaction:)` | Sign and submit          |

### Configuration

| Parameter        | Default    | Description                                                       |
|------------------|------------|-------------------------------------------------------------------|
| `connectors`     | `[]`       | Wallet connectors (e.g. `PhantomWalletConnector`)                 |
| `network`        | `.mainnet` | `.mainnet`, `.devnet`, or `.testnet`                              |
| `persistSession` | `true`     | Set to `false` to disable session persistence across app restarts |
| `logger`         | `nil`      | `(String, String, String) -> Void` for debug logs                 |

### Session Persistence

By default, sessions are persisted to Keychain and restored on the next app launch via `getSession()`. To disable (e.g. for kiosk apps or shared devices):

```swift
let phantom = PhantomClient(
    appId: "your-app-id",
    redirectScheme: "myapp",
    redirectUri: "myapp://phantom-callback",
    persistSession: false
)
```

### Theming

```swift
phantom.theme = .dark   // default
phantom.theme = .light
phantom.theme = .custom(
    sheetBackground: 0xFF1A1A2E,
    optionBackground: 0xFF2A2A3C,
    accentColor: 0xFFAB9FF2,
    textPrimary: 0xFFFFFFFF,
    textSecondary: 0xFF9999AA
)
```

### Logging

```swift
let phantom = PhantomClient(
    appId: "your-app-id",
    redirectScheme: "myapp",
    redirectUri: "myapp://phantom-callback",
    logger: { level, tag, message in
        print("[\(level)] \(tag): \(message)")
    }
)
```

## React Native SDK Mapping

This package provides the same capabilities as the [Phantom Connect React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/index) for native Swift apps.

| React Native SDK                     | Swift                                        |
|--------------------------------------|----------------------------------------------|
| `PhantomProvider` config             | `PhantomClient(...)`                         |
| `config.appId`                       | `appId:`                                     |
| `config.scheme`                      | `redirectScheme:`                            |
| `ConnectButton` / `useModal`         | `phantom.connect()`                          |
| `useConnect({ provider })`           | `phantom.connect(provider:)`                 |
| `useAccounts().isConnected`          | `phantom.getSession() != nil`                |
| `useDisconnect()`                    | `phantom.logout()`                           |
| `useSolana().signMessage`            | `phantom.solana.signMessage(_:)`             |
| `useSolana().signAndSendTransaction` | `phantom.solana.signAndSendTransaction(...)` |
| `useEthereum().signPersonalMessage`  | `phantom.ethereum.personalSign(...)`         |

For official Phantom SDK docs, see [docs.phantom.com](https://docs.phantom.com/wallet-sdks-overview).
