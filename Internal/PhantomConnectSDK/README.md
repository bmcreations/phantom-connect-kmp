# PhantomConnectSDK (iOS)

Swift wrapper around the Kotlin Multiplatform `PhantomConnectKMP` framework. This package provides a Swift-idiomatic API for [Phantom](https://phantom.app) embedded wallets on iOS, equivalent to the [Phantom Connect React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/index) for native Swift apps.

## Installation

Add this package via Swift Package Manager in Xcode:

1. File > Add Package Dependencies
2. Enter the repository URL
3. `import PhantomConnectSDK`

## Usage

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

## API

### `PhantomClient`

| Method               | Description                                                             |
|----------------------|-------------------------------------------------------------------------|
| `connect()`          | Show connect sheet                                                      |
| `connect(provider:)` | Connect with specific provider                                          |
| `createAppWallet()`  | Create app wallet (no OAuth)                                            |
| `getSession()`       | Get current session (restores saved session, auto-renews authenticator) |
| `logout()`           | Clear session and keys                                                  |

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

### SwiftUI

```swift
PhantomClientButton(phantom: phantom) { result in
    // handle result
}
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
