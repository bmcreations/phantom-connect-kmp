# Mock Server for Phantom KMS API

WireMock-based mock server that simulates the Phantom KMS API for E2E testing
without a real Phantom account.

## Quick Start

```bash
# 1. Start mock server
cd maestro/mock-server
docker compose up -d

# 2. Build and install mock flavor of the sample app
./gradlew :sample-android:installMockDebug

# 3. Run E2E tests
maestro test maestro/full-flow.yaml

# 4. Stop mock server when done
docker compose down
```

## Architecture

The mock server uses WireMock with JSON stub mappings:

```
wiremock/
├── mappings/
│   ├── login-page.json           # GET /login → fake OAuth page
│   ├── get-accounts.json         # getAccounts → mock Solana address
│   ├── sign-utf8-message.json    # signUtf8Message → mock signature
│   ├── sign-and-submit-transaction.json  # signAndSubmitTransaction → mock tx hash
│   ├── create-authenticator.json # createAuthenticator → mock auth ID
│   ├── create-organization.json  # createOrganization → mock org ID
│   ├── create-wallet.json        # createWallet → mock wallet ID
│   └── fallback.json             # Catch-all for unknown methods
└── __files/
    └── login.html                # Auto-redirecting fake OAuth page
```

## How It Works

### OAuth Flow
1. SDK opens `http://10.0.2.2:8080/login?redirect_uri=...&provider=google&...`
2. WireMock serves `login.html` which reads the `redirect_uri` from query params
3. After 500ms, JavaScript redirects to `phantomsample://phantom-auth-callback?wallet_id=mock-wallet-123&...`
4. Android intercepts the custom scheme via `PhantomCallbackActivity`
5. SDK parses the redirect params and calls `getAccounts` to populate the wallet address

### JSON-RPC Routing
All KMS API calls go to `POST /v1/wallets` with a JSON-RPC body. WireMock uses
`bodyPatterns` with `matchesJsonPath` to route based on the `method` field.

## Mock Data

| Field | Value |
|-------|-------|
| Wallet ID | `mock-wallet-123` |
| Organization ID | `mock-org-456` |
| Auth User ID | `mock-user-789` |
| Solana Address | `7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU` |
| Signature | `5d2e8a4f1b3c...` (128-char hex) |
| Transaction Hash | `4vJ9JU1bJJE9...` (base58) |

## Build Flavors

The sample app has two flavors:

- **mock** — Points to `http://10.0.2.2:8080` (Android emulator loopback to host)
- **production** — Points to `https://api.phantom.app` (real Phantom API)

Build commands:
```bash
./gradlew :sample-android:installMockDebug       # Mock server
./gradlew :sample-android:installProductionDebug  # Real Phantom API
```

## Maestro Tests

```bash
maestro test maestro/launch-app.yaml           # App launches correctly
maestro test maestro/login-with-google.yaml     # Google OAuth login
maestro test maestro/sign-message.yaml          # Sign a message (after login)
maestro test maestro/logout.yaml                # Logout (after login)
maestro test maestro/full-flow.yaml             # Complete: login → sign → logout
```
