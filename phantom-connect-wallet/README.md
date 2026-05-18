# phantom-connect-wallet

Optional companion module that adds **Phantom wallet app deeplink support** to the Phantom Connect KMP SDK. This enables users to connect and sign via the installed Phantom mobile app using the [Phantom deeplink protocol](https://docs.phantom.com/solana/integrating-phantom/deeplinks-solana), in addition to the core SDK's social login (Google & Apple) flow.

## How It Works

All communication with the Phantom app happens via encrypted deeplinks:

1. **Connect** — Opens Phantom via `https://phantom.app/ul/v1/connect`, performs X25519 key exchange, returns the wallet's public key
2. **Sign** — Each signing operation deeplinks to Phantom, the user approves in-app, and the signed result comes back via your callback scheme
3. **Encryption** — All payloads after connect are encrypted with NaCl secretbox using the shared secret derived from the X25519 handshake

Solana only — Phantom deeplinks don't support Ethereum signing.

## Installation

### Android (Gradle)

```kotlin
dependencies {
    implementation("com.phantom:phantom-connect:<version>")
    implementation("com.phantom:phantom-connect-wallet:<version>")
}
```

### iOS (Swift Package Manager)

The wallet module is bundled into the core `PhantomConnectSDK` Swift package — no separate dependency needed. The `PhantomWalletConnector` and `IosDeeplinkLauncher` types are available directly via `import PhantomConnectSDK`.

## Setup

### Android

```kotlin
import dev.bmcreations.phantom.connect.wallet.PhantomWalletConnector
import dev.bmcreations.phantom.connect.wallet.createDeeplinkLauncher

val walletConnector = PhantomWalletConnector(
    deeplinkLauncher = createDeeplinkLauncher(applicationContext),
    appUrl = "https://your-app.example.com",
    redirectUrl = "myapp://phantom-wallet-callback",
)

val sdk = PhantomSdk.create(
    config = PhantomSdkConfig(
        appId = "your-app-id",
        redirectScheme = "myapp",
        redirectUri = "myapp://phantom-auth-callback",
    ),
    oauthLauncher = createOAuthLauncher(this),
    connectors = listOf(walletConnector),
)
```

The module includes an `AndroidManifest.xml` with the `<queries>` declaration for `app.phantom`, which merges automatically via the Android manifest merger.

### iOS (Swift)

```swift
import PhantomConnectSDK

let connector = PhantomWalletConnector(
    deeplinkLauncher: createDeeplinkLauncher(),
    appUrl: "https://your-app.example.com",
    redirectUrl: "myapp://phantom-wallet-callback"
)

let phantom = PhantomClient(
    appId: "your-app-id",
    redirectScheme: "myapp",
    redirectUri: "myapp://phantom-auth-callback",
    connectors: [connector]
)
```

Handle the callback URL in your SwiftUI app:

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

## Parameters

| Parameter | Description |
|-----------|-------------|
| `deeplinkLauncher` | Platform-specific deeplink handler — use `createDeeplinkLauncher()` factory (Android requires a `Context` argument) |
| `appUrl` | Your app's HTTPS URL — used by Phantom to identify the dapp. Must be a valid URL, not a custom scheme. |
| `redirectUrl` | Full redirect URL for deeplink callbacks (e.g. `"myapp://phantom-wallet-callback"` for custom schemes, or `"https://yourapp.com/callback"` for HTTPS universal links). |

## Availability Check

`PhantomWalletConnector` implements `isAppInstalled()` to detect whether the Phantom app is present on the device. The SDK calls this before opening the connect sheet — connectors that aren't available are shown as disabled with a "Not available" subtitle.

On **Android**, this uses `PackageManager.getPackageInfo("app.phantom")`. The wallet module's `AndroidManifest.xml` includes the required `<queries>` declaration, which merges automatically.

On **iOS**, `canOpenURL("phantom://")` requires the consumer to add `phantom` to `LSApplicationQueriesSchemes` in their `Info.plist`. Since an SDK can't enforce plist entries, `isAppInstalled()` always returns `true` on iOS. If Phantom isn't installed, the deeplink `launch()` call handles the failure gracefully with an error shown in the sheet.

## Usage

### Connect via the sheet

When connectors are provided, the connect sheet automatically includes them alongside the social login buttons. Each connector's `callToAction` (default: `"Continue with {displayName}"`) is used as the button label. The SDK checks each connector's availability before opening the sheet.

```kotlin
// Kotlin — sheet includes wallet connector automatically
val result = sdk.connect()
```

```swift
// Swift — sheet includes wallet connector automatically
let result = await phantom.connect()
```

### Connect directly (bypass sheet)

```kotlin
// Kotlin
val result = sdk.connect(walletConnector)
```

```swift
// Swift
let result = await phantom.connect(connector: connector)
```

### Signing

After connecting via deeplink, all signing operations route through the Phantom app:

```kotlin
// Kotlin
val signature = sdk.solana.signMessage("Hello from my app!")
val txSig = sdk.solana.signAndSendTransaction(transactionBase58)
```

```swift
// Swift
let signature = try await phantom.solana.signMessage("Hello from my app!")
let txSig = try await phantom.solana.signAndSendTransaction(base64Transaction: tx)
```

## Session Persistence

Deeplink wallet sessions are persisted alongside social login sessions. The connector's crypto state (X25519 keypair, shared secret, session token) is serialized and saved with the session. On app restart, calling `getSession()` restores both the session and the connector state, so subsequent signing operations work without re-connecting.

## Architecture

```
phantom-connect-wallet/
  commonMain/
    PhantomWalletConnector.kt    — WalletConnector implementation
    PhantomDeeplinkProtocol.kt   — X25519 key exchange, NaCl encryption, URL building
    DeeplinkLauncher.kt          — Platform-agnostic deeplink interface
  androidMain/
    AndroidDeeplinkLauncher.kt   — Intent-based deeplink launching
    PhantomWalletCallbackActivity.kt — Transparent activity for receiving callbacks
  iosMain/
    IosDeeplinkLauncher.kt       — UIApplication.openURL-based deeplink launching
```

## Crypto

The deeplink protocol uses:
- **X25519** (Curve25519 Diffie-Hellman) for key exchange during connect
- **NaCl secretbox** (XSalsa20-Poly1305) for encrypting/decrypting all subsequent payloads
- **libsodium** via `multiplatform-crypto-libsodium-bindings` — same library as the core SDK

All crypto runs in shared Kotlin code on both platforms.
