# Phantom Connect KMP SDK

Kotlin Multiplatform SDK for Phantom Connect embedded wallets (Google/Apple social login). Reverse-engineered from the MIT-licensed `phantom/phantom-connect-sdk` JS monorepo.

## Project Structure

```
phantom-connect/     # KMP library module (commonMain, androidMain, iosMain)
sample-android/      # Android sample app (mock + production build flavors)
Internal/            # iOS SPM distribution (Swift wrapper + XCFramework)
maestro/             # E2E test flows (Maestro)
scripts/             # Test runner scripts (run-e2e-tests.sh)
```

## Build & Run

```bash
# Build the SDK
./gradlew :phantom-connect:assemble

# Run unit tests (all platforms)
./gradlew :phantom-connect:allTests

# Run Android unit tests only
./gradlew :phantom-connect:testDebugUnitTest

# Run iOS simulator tests
./gradlew :phantom-connect:iosSimulatorArm64Test

# Build sample app (mock flavor for testing)
./gradlew :sample-android:assembleMockDebug

# Build sample app (production)
./gradlew :sample-android:assembleProductionDebug

# Run E2E tests (requires Docker for WireMock)
./scripts/run-e2e-tests.sh

# Build iOS XCFramework
./gradlew :phantom-connect:assemblePhantomConnectKMPReleaseXCFramework
```

## Key Architecture Decisions

- **Ed25519 keypair is the authenticator credential**, not just an HTTP signing key
- **libsodium** (multiplatform-crypto-libsodium-bindings) for Ed25519 on all platforms — not platform-native crypto
- **Interfaces over mocks**: Ed25519KeyStoreProvider, SessionStoreProvider, OAuthLauncher, TimeProvider — all swappable with fakes for testing
- **PhantomSdk.create() / PhantomSdk.createForTesting()** factory pattern for DI
- **Single KMS endpoint**: POST `https://api.phantom.app/v1/wallets` with JSON-RPC envelopes
- **Android OAuth**: Custom Tabs + CompletableDeferred (not ActivityResultLauncher)
- **iOS OAuth**: ASWebAuthenticationSession on main thread

## Testing Conventions

- Unit tests use fakes and interfaces, not mocks
- E2E tests use WireMock (Docker) + Maestro + `mock` build flavor with MockOAuthLauncher
- Android unit tests need JVM libsodium dependency (already configured)

## Tech Stack

- Kotlin Multiplatform (Android + iOS)
- Compose Multiplatform (UI components)
- Ktor (HTTP client)
- kotlinx.serialization (JSON)
- kotlinx.datetime
- KMMBridge (iOS SPM distribution)
- Android: minSdk 24, compileSdk 35, JVM 21
- iOS: 16+
