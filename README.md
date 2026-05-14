# Phantom Connect KMP SDK

Kotlin Multiplatform SDK for [Phantom](https://phantom.app) embedded wallets. Add social login (Google & Apple) and wallet operations to your Android and iOS apps with a single shared codebase.

Built from the official [phantom-connect-sdk](https://github.com/phantom/phantom-connect-sdk) and designed to match the [React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/index) feature set. If you're migrating from the React Native SDK, see the [API mapping](#react-native-sdk-mapping) below.

## Features

- **Social login** -- Google and Apple sign-in via secure system browser
- **Phantom wallet app** -- Connect and sign via the installed Phantom app using [deeplinks](https://docs.phantom.com/solana/integrating-phantom/deeplinks-solana) (optional `phantom-connect-wallet` module)
- **Chain-scoped signing** -- `sdk.solana` and `sdk.ethereum` with chain-specific methods
- **Solana** -- `signMessage`, `signTransaction`, `signAndSendTransaction`, `signAllTransactions`
- **Ethereum** -- `personalSign` (EIP-191), `signTypedData` (EIP-712), `signTransaction`, `signAndSendTransaction`
- **App wallets** -- Programmatic wallet creation without OAuth
- **Session persistence** -- Encrypted storage (EncryptedSharedPreferences on Android, Keychain on iOS), or opt out with `persistSession = false`
- **Auto-renewal** -- Ed25519 authenticator keys rotate automatically before expiry
- **Session restore** -- Optionally restore saved sessions on app launch via `getSession()`
- **Connect sheet** -- Built-in Compose Multiplatform bottom sheet UI with dark/light/custom themes
- **Debug logging** -- Optional structured logging via `PhantomLogger`
- **CAIP-2 network IDs** -- `solana:mainnet`, `eip155:1`, etc.

## Requirements

| Platform | Minimum Version |
|----------|----------------|
| Android  | API 24 (Android 7.0) |
| iOS      | 16.0 |
| Kotlin   | 2.1+ |
| JVM      | 21 |

## Prerequisites

Create an app in [Phantom Portal](https://portal.phantom.app) to get your `appId`. Configure your redirect URI (e.g. `myapp://phantom-auth-callback`).

## Installation

### Android (Gradle)

Add the SDK dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.phantom:phantom-connect:<version>")
}
```

### iOS (Swift Package Manager)

Add the package via Xcode:

1. File > Add Package Dependencies
2. Enter the repository URL
3. Import `PhantomConnectSDK` in your Swift files

## Quick Start

### Android (Kotlin)

```kotlin
import dev.bmcreations.phantom.connect.*

// 1. Initialize in Application.onCreate()
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PhantomSdk.init(this)
    }
}

// 2. Create the SDK in your Activity
val sdk = PhantomSdk.create(
    config = PhantomSdkConfig(
        appId = "your-app-id",
        redirectScheme = "myapp",
        redirectUri = "myapp://phantom-auth-callback",
    ),
    oauthLauncher = createOAuthLauncher(this),
)

// 3. Connect with the built-in sheet
val result = sdk.connect()
when (result) {
    is ConnectResult.Success -> {
        val address = sdk.solana.getAddress()
        println("Connected: $address")
    }
    is ConnectResult.Cancelled -> { /* user dismissed */ }
    is ConnectResult.Error -> { /* handle error */ }
}

// 4. Sign a message
val signature = sdk.solana.signMessage("Hello from Phantom!")

// 5. Sign and send a transaction
val txHash = sdk.solana.signAndSendTransaction(transactionBase64)
```

### iOS (Swift)

```swift
import PhantomConnectSDK

// 1. Create the client
let phantom = PhantomClient(
    appId: "your-app-id",
    redirectScheme: "myapp",
    redirectUri: "myapp://phantom-auth-callback"
)

// 2. Connect
let result = await phantom.connect()
switch result {
case .success(let session):
    let address = await phantom.solana.getAddress()
    print("Connected: \(address ?? "none")")
case .cancelled:
    break
case .error(let error):
    print("Error: \(error)")
}

// 3. Sign a message
let signature = try await phantom.solana.signMessage("Hello from Phantom!")

// 4. Sign and send a transaction
let txHash = try await phantom.solana.signAndSendTransaction(base64Transaction: tx)

// 5. Restore session on launch (optional -- in SwiftUI)
.task {
    if let session = await phantom.getSession() {
        // update your state with restored session
    }
}
```

## Wallet Connector (Phantom App Deeplinks)

The optional `phantom-connect-wallet` module adds support for connecting and signing via the installed Phantom mobile app. All communication happens through encrypted deeplinks using the [Phantom deeplink protocol](https://docs.phantom.com/solana/integrating-phantom/deeplinks-solana). Solana only.

### Android

```kotlin
import dev.bmcreations.phantom.connect.wallet.PhantomWalletConnector
import dev.bmcreations.phantom.connect.wallet.createDeeplinkLauncher

val walletConnector = PhantomWalletConnector(
    deeplinkLauncher = createDeeplinkLauncher(applicationContext),
    appUrl = "https://your-app.example.com",
    callbackScheme = "myapp",
)

val sdk = PhantomSdk.create(
    config = PhantomSdkConfig(/* ... */),
    oauthLauncher = createOAuthLauncher(this),
    connectors = listOf(walletConnector),
)
```

### iOS (Swift)

The wallet types are included in the `PhantomConnectSDK` package -- no separate dependency.

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
    redirectUri: "myapp://phantom-auth-callback",
    connectors: [connector]
)

// Handle deeplink callbacks in your SwiftUI app:
.onOpenURL { url in
    IosDeeplinkLauncher.handleCallback(url: url)
}
```

When connectors are provided, the connect sheet automatically includes them — each connector's `callToAction` (default: `"Continue with {displayName}"`) is used as the button label. You can also connect directly via `sdk.connect(walletConnector)` / `phantom.connect(connector:)`.

For full details, see the [wallet module README](phantom-connect-wallet/README.md).

## API Reference

### Configuration

```kotlin
PhantomSdkConfig(
    appId = "your-app-id",           // Required: from Phantom Portal
    redirectScheme = "myapp",         // Required: your app's URL scheme
    redirectUri = "myapp://callback", // Required: full redirect URI
    chains = listOf(Chain.Solana),    // Chains to fetch addresses for (default: Solana)
    providers = AuthProvider.all,     // Auth providers to offer (default: Google + Apple)
    network = Network.Mainnet,        // Network environment (default: Mainnet)
    persistSession = true,            // Set to false to disable session persistence across app restarts
    logger = PhantomLogger { level, tag, message ->
        Log.d(tag, "[$level] $message")
    },
)
```

### Connection

| Method | Description |
|--------|-------------|
| `connect()` | Show the connect sheet and let the user choose a provider |
| `connect(provider)` | Connect with a specific provider directly |
| `connect(connector)` | Connect with a wallet connector directly (e.g. Phantom app deeplink) |
| `createAppWallet()` | Create a programmatic app wallet (no OAuth) |
| `logout()` | Clear session and keys |

### Session

| Method | Description |
|--------|-------------|
| `getSession()` | Get the current session (auto-renews authenticator if needed) |
| `isConnected()` | Whether there is an active session |
| `getAddress(chain)` | First address for a chain, or null |
| `getAddresses()` | All addresses from the current session |

### Solana Operations (`sdk.solana`)

| Method | Description |
|--------|-------------|
| `getAddress()` | Get the Solana address |
| `signMessage(message)` | Sign a UTF-8 message |
| `signTransaction(base64)` | Sign a transaction without broadcasting |
| `signAndSendTransaction(base64)` | Sign and submit a transaction |
| `signAllTransactions(list)` | Batch sign multiple transactions in a single call |

### Ethereum Operations (`sdk.ethereum`)

| Method | Description |
|--------|-------------|
| `getAddress()` | Get the Ethereum address |
| `personalSign(message)` | EIP-191 personal_sign |
| `signTypedData(json)` | EIP-712 signTypedData_v4 |
| `signTransaction(base64)` | Sign a transaction without broadcasting |
| `signAndSendTransaction(base64)` | Sign and submit a transaction |

### Theming

The connect sheet supports dark (default), light, and custom themes:

```kotlin
// Kotlin
sdk.theme = ConnectSheetTheme.Dark
sdk.theme = ConnectSheetTheme.Light
sdk.theme = ConnectSheetTheme.Custom(
    sheetBackground = 0xFF1A1A2EL,
    optionBackground = 0xFF2A2A3CL,
    accentColor = 0xFFAB9FF2L,
    textPrimary = 0xFFFFFFFFL,
    textSecondary = 0xFF9999AAL,
)
```

```swift
// Swift
phantom.theme = .dark
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

Pass a logger to receive structured SDK diagnostics:

```kotlin
// Kotlin
val config = PhantomSdkConfig(
    // ...
    logger = PhantomLogger { level, tag, message ->
        Log.d("Phantom/$tag", "[$level] $message")
    },
)
```

```swift
// Swift
let phantom = PhantomClient(
    appId: "your-app-id",
    redirectScheme: "myapp",
    redirectUri: "myapp://phantom-auth-callback",
    logger: { level, tag, message in
        print("[\(level)] \(tag): \(message)")
    }
)
```

## Architecture

```
phantom-connect/         Core KMP library (commonMain, androidMain, iosMain)
  commonMain/            Shared business logic, models, Compose UI
  androidMain/           EncryptedSharedPreferences, Custom Tabs OAuth
  iosMain/               Keychain storage, ASWebAuthenticationSession OAuth
phantom-connect-wallet/  Optional wallet connector module (deeplink protocol)
  commonMain/            X25519 key exchange, NaCl encryption, URL building
  androidMain/           Intent-based deeplink launching
  iosMain/               UIApplication.openURL-based deeplink launching
Internal/                iOS SPM distribution (Swift wrapper + XCFramework)
sample-android/          Android sample app
sample-ios/              iOS sample app
```

### Security

- **Ed25519 keypair** is the authenticator credential, generated via libsodium
- **Android**: Keys stored in EncryptedSharedPreferences (AES-256-GCM); OAuth via Chrome Custom Tabs
- **iOS**: Keys stored in Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`); OAuth via ASWebAuthenticationSession
- **Authenticator auto-renewal**: Keys rotate automatically within a 2-day window before the 7-day expiry
- **KMS signing**: All signing operations go through `POST https://api.phantom.app/v1/wallets/kms/rpc` with Ed25519-signed request stamps
- **Transaction preparation**: User wallet transactions are routed through `/v1/wallets/prepare` for spending-limit simulation before signing

### Testing

The SDK uses interfaces and fakes for testing -- no mocks:

```
Ed25519KeyStoreProvider  -> FakeEd25519KeyStore
SessionStoreProvider     -> FakeSessionStore
OAuthLauncher            -> FakeOAuthLauncher
TimeProvider             -> FakeTimeProvider
```

```bash
# All unit tests (Android + iOS)
./gradlew :phantom-connect:allTests

# Android only
./gradlew :phantom-connect:testDebugUnitTest

# iOS simulator only
./gradlew :phantom-connect:iosSimulatorArm64Test
```

## Sample Apps

Both sample apps demonstrate the full connect, sign, and disconnect flow. Each supports a mock build flavor/configuration for testing with a local WireMock server.

### Android

```bash
# Production
./gradlew :sample-android:assembleProductionDebug

# Mock (for testing with WireMock)
./gradlew :sample-android:assembleMockDebug
```

### iOS

Open `sample-ios/PhantomSample.xcodeproj` in Xcode. Set the `PHANTOM_MOCK` environment variable to use the mock configuration.

## Session Persistence

By default, sessions are persisted to platform-secure storage and restored automatically on the next app launch via `getSession()`. Sessions are maintained for seven days with automatic authenticator renewal.

To disable persistence (e.g. for kiosk apps or shared devices where users should connect fresh each launch):

```kotlin
// Kotlin
val config = PhantomSdkConfig(
    // ...
    persistSession = false,
)
```

```swift
// Swift
let phantom = PhantomClient(
    // ...
    persistSession: false
)
```

When `persistSession` is `false`, sessions are held in memory only. They work normally within a single app lifecycle but are lost on app restart.

## React Native SDK Mapping

This SDK provides the same capabilities as the [Phantom Connect React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/index) for native Kotlin and Swift apps. Here's how the APIs correspond:

| React Native SDK | KMP SDK (Kotlin) | KMP SDK (Swift) |
|------------------|-------------------|-----------------|
| `PhantomProvider` config | `PhantomSdkConfig(...)` | `PhantomClient(...)` |
| `config.appId` | `config.appId` | `appId:` |
| `config.scheme` | `config.redirectScheme` | `redirectScheme:` |
| `config.providers` | `config.providers` | (always Google + Apple) |
| `config.addressTypes` | `config.chains` | (always Solana) |
| `ConnectButton` / `useModal` | `sdk.connect()` | `phantom.connect()` |
| `useConnect({ provider })` | `sdk.connect(provider)` | `phantom.connect(provider:)` |
| `useAccounts().isConnected` | `sdk.isConnected()` | `phantom.getSession() != nil` |
| `useAccounts().addresses` | `sdk.getAddresses()` | `session.addresses` |
| `useDisconnect()` | `sdk.logout()` | `phantom.logout()` |
| `useSolana().signMessage` | `sdk.solana.signMessage(msg)` | `phantom.solana.signMessage(msg)` |
| `useSolana().signAndSendTransaction` | `sdk.solana.signAndSendTransaction(tx)` | `phantom.solana.signAndSendTransaction(tx)` |
| `useEthereum().signPersonalMessage` | `sdk.ethereum.personalSign(msg)` | `phantom.ethereum.personalSign(msg)` |
| `useEthereum().signTypedData` | `sdk.ethereum.signTypedData(json)` | `phantom.ethereum.signTypedData(json)` |

For full React Native SDK documentation, see [docs.phantom.com](https://docs.phantom.com/sdks/react-native-sdk/index).

## Contributing

1. Fork the repo
2. Create a feature branch
3. Make your changes with tests
4. Run `./gradlew :phantom-connect:allTests` to verify
5. Open a pull request

## License

[MIT](LICENSE)
