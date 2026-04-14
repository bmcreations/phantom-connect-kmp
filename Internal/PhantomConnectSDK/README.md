# PhantomConnectSDK

Native Swift implementation of the [Phantom Connect SDK](https://github.com/phantom/phantom-connect-sdk) for iOS. Provides the same embedded wallet capabilities as the [React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/index) — social login (Google/Apple), message signing, and transaction handling — without requiring React Native or any JavaScript bridge. Also includes optional wallet app deeplink support, extending beyond the original SDK.

## Installation

Add this package via Swift Package Manager in Xcode:

1. File > Add Package Dependencies
2. Enter the repository URL: `https://github.com/bmcreations/phantom-connect-ios`
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

// Restore session on launch
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

When connectors are provided, the connect sheet automatically includes them alongside social login buttons:

```swift
let result = await phantom.connect()  // sheet includes wallet option
```

### Connect Directly

Bypass the sheet and connect with the Phantom app directly:

```swift
let result = await phantom.connect(connector: connector)
```

### Connector Parameters

| Parameter          | Description                                                                                                                     |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `deeplinkLauncher` | `createDeeplinkLauncher()` -- handles opening Phantom and receiving callbacks                                                   |
| `appUrl`           | Your app's HTTPS URL, used by Phantom to identify the dapp. Must be a valid URL, not a custom scheme.                           |
| `callbackScheme`   | URL scheme for deeplink callbacks (e.g. `"myapp"`). Phantom sends results back to `{callbackScheme}://phantom-wallet-callback`. |

## API

### `PhantomClient`

| Method                | Description                                                             |
|-----------------------|-------------------------------------------------------------------------|
| `connect()`           | Show connect sheet (includes wallet connectors if provided)             |
| `connect(provider:)`  | Connect with specific social provider (`.google` or `.apple`)           |
| `connect(connector:)` | Connect with a wallet connector directly                                |
| `createAppWallet()`   | Create app wallet (no OAuth)                                            |
| `getSession()`        | Get current session (restores saved session, auto-renews authenticator) |
| `logout()`            | Clear session and keys                                                  |

### `phantom.solana`

| Method                                       | Description        |
|----------------------------------------------|--------------------|
| `getAddress()`                               | Solana address     |
| `signMessage(_:)`                            | Sign UTF-8 message |
| `signTransaction(base64Transaction:)`        | Sign transaction   |
| `signAndSendTransaction(base64Transaction:)` | Sign and submit    |
| `signAllTransactions(base64Transactions:)`   | Batch sign         |

### `phantom.ethereum`

| Method                                       | Description              |
|----------------------------------------------|--------------------------|
| `getAddress()`                               | Ethereum address         |
| `personalSign(message:)`                     | EIP-191 personal_sign    |
| `signTypedData(typedDataJson:)`              | EIP-712 signTypedData_v4 |
| `signTransaction(base64Transaction:)`        | Sign transaction         |
| `signAndSendTransaction(base64Transaction:)` | Sign and submit          |

## Configuration

| Parameter        | Default    | Description                                                       |
|------------------|------------|-------------------------------------------------------------------|
| `connectors`     | `[]`       | Wallet connectors (e.g. `PhantomWalletConnector`)                 |
| `network`        | `.mainnet` | `.mainnet`, `.devnet`, or `.testnet`                              |
| `persistSession` | `true`     | Set to `false` to disable session persistence across app restarts |
| `logger`         | `nil`      | `(String, String, String) -> Void` for debug logs                 |

## Session Persistence

By default, sessions are persisted to Keychain and restored on the next app launch via `getSession()`. Deeplink wallet sessions (connector crypto state, shared secret, session token) are saved alongside social login sessions.

To disable persistence (e.g. for kiosk apps or shared devices):

```swift
let phantom = PhantomClient(
    appId: "your-app-id",
    redirectScheme: "myapp",
    redirectUri: "myapp://phantom-callback",
    persistSession: false
)
```

When disabled, sessions are kept in memory only and lost when the app is terminated.

## Theming

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

## Logging

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

## Requirements

- iOS 16+
- Swift 5.9+

## Links

- [Phantom Documentation](https://docs.phantom.com)
- [Phantom Developer Portal](https://phantom.app)
