# /upstream-sync — Sync with upstream phantom-connect-sdk

Synchronize this KMP SDK with changes from the upstream `phantom/phantom-connect-sdk` JS/TS monorepo.

## Upstream Repository

- **Repo:** `https://github.com/phantom/phantom-connect-sdk` (MIT)
- **Packages of interest:**
  - `packages/phantom-connect-sdk/src/` — core SDK logic (auth, session, crypto, network)
  - `packages/phantom-connect-expo/src/` — Expo/RN auth provider (ExpoAuthProvider, SecureStore usage)

## File Mapping (upstream TS → KMP Kotlin)

| Upstream (TS) | KMP (Kotlin) |
|---|---|
| `PhantomConnectSDK.ts` | `PhantomSdk.kt` |
| `auth/authFlow.ts`, `auth/auth2Flow.ts` | `internal/auth/AuthOrchestrator.kt` |
| `auth/legacyLogin.ts` | `internal/auth/AuthOrchestrator.kt` (legacy flow section) |
| `auth/pkce.ts` | `internal/auth/PkceFlow.kt` |
| `auth/jarBuilder.ts` | `internal/auth/JarBuilder.kt` |
| `auth/auth2Token.ts` | `internal/auth/Auth2Token.kt` |
| `auth/tokenExchange.ts` | `internal/auth/TokenExchange.kt` |
| `crypto/stamper.ts` (Ed25519) | `internal/crypto/Ed25519Stamper.kt` |
| `crypto/oidcStamper.ts` | `internal/crypto/OidcStamper.kt` |
| `crypto/p256.ts` | `internal/crypto/P256KeyStoreProvider.kt` |
| `network/walletService.ts` | `internal/network/PhantomClient.kt` |
| `network/jsonRpc.ts` | `internal/network/JsonRpc.kt` |
| `network/solanaRpc.ts` | `internal/network/SolanaRpcClient.kt` |
| `network/walletServiceError.ts` | `internal/network/WalletServiceError.kt` |
| `session/sessionStore.ts` | `internal/auth/SessionStoreProvider.kt` + platform impls |
| `types.ts`, `models.ts` | `Models.kt` |
| `hooks/useConnect.ts`, `hooks/useSolana.ts` | `SolanaOperations.kt`, `EthereumOperations.kt` |
| `expo/ExpoAuthProvider.ts` | `OAuthLauncher.kt` + platform impls (`AndroidOAuthLauncher.kt`, iOS ASWebAuth) |

## Sync Procedure

### Phase 1: Identify upstream changes

1. Fetch the upstream repo (or browse it on GitHub) and diff against the last synced tag/commit
2. The last synced upstream version is recorded in the most recent git tag on this repo (currently `v2.0.2`)
3. Focus on `packages/phantom-connect-sdk/src/` — this is the core logic
4. Categorize changes: new features, bug fixes, API changes, new endpoints, config changes

### Phase 2: Analyze impact

For each upstream change, determine:
- **Which KMP file(s)** are affected (use the file mapping above)
- **Is it a direct port** (logic translates 1:1) or **requires adaptation** (platform-specific, async model differences)
- **Does it affect the public API** (`PhantomSdk.kt`, `Models.kt`, `SolanaOperations.kt`, `EthereumOperations.kt`)
- **Does it need platform-specific implementations** (new `expect`/`actual` functions)

Key translation patterns:
- `async/await` → `suspend fun`
- `Promise.all([...])` → `coroutineScope { list.map { async { ... } }.awaitAll() }`
- `SecureStore.setItemAsync/getItemAsync` → `SessionStoreProvider` / platform stores
- `expo-web-browser` → `OAuthLauncher` (Custom Tabs on Android, ASWebAuthenticationSession on iOS)
- `@turnkey/crypto` P-256 → `P256KeyStoreProvider` (Android Keystore / iOS SecKey)
- `tweetnacl` Ed25519 → libsodium via `multiplatform-crypto-libsodium-bindings`
- TypeScript interfaces/types → `@Serializable data class` with `kotlinx.serialization`
- `fetch()` → Ktor `HttpClient`
- `Buffer.from(x, 'base64')` → `kotlin.io.encoding.Base64`

### Phase 3: Implement changes

1. **Create a feature branch:** `sync/upstream-<version>`
2. **Port changes file by file**, following the mapping. Keep commits granular:
   - One commit per logical upstream change (not per file)
   - Prefix: `feat:`, `fix:`, `refactor:` matching the nature of the upstream change
3. **Update platform implementations** if new `expect` declarations are needed
4. **Update `Models.kt`** if upstream types changed (new fields, renamed fields, new enums)
5. **Update sample apps** if the public API changed

### Phase 4: Verify

1. `./gradlew :phantom-connect:testDebugUnitTest` — unit tests pass
2. `./gradlew :phantom-connect:assemble` — full build (Android + iOS frameworks)
3. If auth flow changed: manual test with sample apps (both mock and production flavors)
4. If network/signing changed: E2E test with `./scripts/run-e2e-tests.sh`

### Phase 5: Tag and publish

1. Update version tag to match upstream: `git tag -a v<version> -m "v<version>"`
2. Push tag: `git push origin v<version>`
3. Publish to Maven Central: `./gradlew publishAllPublicationsToMavenCentralRepository -PVERSION_NAME=<version>`
4. Build and publish iOS SPM: `./gradlew :phantom-connect:assemblePhantomConnectKMPReleaseXCFramework`

## Common Pitfalls

- **Upstream uses `SecureStore` for everything** (session + flags). KMP splits into encrypted storage (session) and plain storage (boolean flags like `shouldClearPreviousSession`)
- **Upstream's `stamper` is always Ed25519**. KMP has `stamperOverride` for auth2 OIDC sessions that use P-256
- **Upstream has no `InMemorySessionStore`** — KMP added this for `persistSession=false` mode
- **Upstream uses `expo-auth-session`** for OAuth. KMP uses platform-native browser APIs with `CompletableDeferred`
- **Base64 encoding:** upstream uses Node `Buffer`, KMP uses `kotlin.io.encoding.Base64` — watch for URL-safe vs standard variants
- **JSON-RPC envelope:** both use the same structure, but watch for new methods or changed parameter shapes
