package dev.bmcreations.phantom.connect.internal.auth

import dev.bmcreations.phantom.connect.*
import dev.bmcreations.phantom.connect.WalletConnector
import dev.bmcreations.phantom.connect.WalletConnectorResult
import dev.bmcreations.phantom.connect.internal.crypto.*
import dev.bmcreations.phantom.connect.internal.network.PhantomClient
import dev.bmcreations.phantom.connect.internal.network.SolanaRpcClient
import dev.bmcreations.phantom.connect.internal.network.SpendingLimitError
import dev.bmcreations.phantom.connect.internal.network.isAuthenticationError
import dev.bmcreations.phantom.connect.internal.platform.SdkLogger
import dev.bmcreations.phantom.connect.internal.platform.SystemTimeProvider
import dev.bmcreations.phantom.connect.internal.platform.TimeProvider
import dev.bmcreations.phantom.connect.internal.platform.getPlatform
import dev.bmcreations.phantom.connect.internal.platform.sdkType
import io.ktor.client.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.days
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "AuthOrchestrator"

internal class AuthOrchestrator(
    private val client: PhantomClient,
    private val keyStore: Ed25519KeyStoreProvider,
    private val sessionStore: SessionStoreProvider,
    private val oauthLauncher: OAuthLauncher,
    private val stamper: Ed25519Stamper,
    private val config: PhantomSdkConfig,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val connectors: List<WalletConnector> = emptyList(),
    private val solanaRpcClient: SolanaRpcClient? = null,
    private val p256KeyStore: P256KeyStoreProvider? = null,
    private val httpClient: HttpClient? = null,
) {
    companion object {
        private val AUTHENTICATOR_TTL = 31.days
        private val RENEWAL_WINDOW = 2.days
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Event system
    private val _events = MutableSharedFlow<PhantomEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<PhantomEvent> = _events.asSharedFlow()

    // Track whether we should clear previous session on next connect (loaded from store)
    private var shouldClearPreviousSession = false
    private var shouldClearLoaded = false

    // OIDC stamper for auth2 sessions — persisted across signing calls, restored on app restart
    private var oidcStamper: OidcStamper? = null

    private fun findConnector(providerId: String): WalletConnector? =
        connectors.firstOrNull { it.id == providerId }

    /**
     * Connect with a social provider (Google, Apple).
     *
     * Uses the PKCE/Auth2 flow:
     * 1. Generate P-256 keypair
     * 2. Generate PKCE code verifier + challenge
     * 3. Build JAR (signed JWT) with auth claims
     * 4. Launch browser → redirect back with authorization code
     * 5. Exchange auth code for tokens
     * 6. Get/create organization and wallet via KMS
     * 7. Handle pending migrations
     * 8. Fetch addresses and save session
     *
     * Falls back to legacy Ed25519 flow if P-256 key store is not available.
     */
    private suspend fun ensureShouldClearLoaded() {
        if (!shouldClearLoaded) {
            shouldClearPreviousSession = sessionStore.loadShouldClearPreviousSession()
            shouldClearLoaded = true
        }
    }

    private suspend fun persistShouldClear(value: Boolean) {
        shouldClearPreviousSession = value
        sessionStore.saveShouldClearPreviousSession(value)
    }

    /**
     * Auto-connect: silently try to restore an existing session.
     * On failure, sets shouldClearPreviousSession and emits ConnectError.
     * Returns the session on success, null on failure.
     */
    suspend fun autoConnect(): PhantomSession? {
        return try {
            val session = getSession()
            if (session != null) {
                _events.tryEmit(PhantomEvent.Connected(session, "auto-connect"))
            }
            session
        } catch (e: Exception) {
            SdkLogger.warn(TAG, "Auto-connect failed: ${e.message}")
            persistShouldClear(true)
            _events.tryEmit(PhantomEvent.ConnectError(e, "auto-connect"))
            null
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun connectWithSocial(provider: AuthProvider): ConnectResult {
        ensureShouldClearLoaded()
        SdkLogger.info(TAG, "Connect started with provider=${provider.id}")
        _events.tryEmit(PhantomEvent.ConnectStart(provider.id, "social"))

        // Legacy Ed25519 redirect flow — matches upstream RN SDK's active ExpoAuthProvider.
        // Auth2 PKCE flow is available via connectWithAuth2() but the upstream RN SDK
        // currently uses the legacy redirect flow in production.
        return connectWithLegacyFlow(provider)
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun connectWithAuth2(provider: AuthProvider): ConnectResult {
        val p256 = p256KeyStore!!
        val http = httpClient!!

        try {
            // Phase 1: Generate P-256 keypair
            p256.delete(P256KeyStoreTags.ACTIVE)
            p256.delete(P256KeyStoreTags.PENDING)
            val publicKey = p256.generateKeyPair(P256KeyStoreTags.PENDING)

            // Phase 2: Generate PKCE verifier + challenge
            val codeVerifier = PkceFlow.generateCodeVerifier()
            val codeChallenge = PkceFlow.generateCodeChallenge(codeVerifier)

            // Phase 3: Derive nonce and build JAR
            val salt = "" // Empty salt per upstream
            val nonce = PkceFlow.deriveNonce(publicKey, salt)
            val sessionId = Uuid.random().toString()

            val jarBuilder = JarBuilder(p256, timeProvider)
            val loginHint = when (provider) {
                is AuthProvider.Phantom, is AuthProvider.Device -> null
                else -> "${provider.id}:auth2"
            }

            // The connectLoginUrl is the login page URL — used as both the JAR audience
            // and the base URL for the browser redirect (per upstream auth2Flow.ts)
            val connectLoginUrl = "${config.loginBaseUrl}/login/start"

            val signedJar = jarBuilder.buildJar(
                keyTag = P256KeyStoreTags.PENDING,
                aud = connectLoginUrl,
                clientId = config.appId,
                redirectUri = config.redirectUri,
                nonce = nonce,
                codeChallenge = codeChallenge,
                loginHint = loginHint,
                state = sessionId,
                shouldMigrate = true,
            )

            // Phase 4: Build login URL and launch browser
            val loginUrl = "$connectLoginUrl#jar=${urlEncode(signedJar)}"

            // Save pending session so we can resume after redirect
            val pendingSession = PhantomSession(
                walletId = "",
                organizationId = "",
                providerId = provider.id,
                accountDerivationIndex = 0,
                sessionId = sessionId,
                expiresAt = 0,
                authenticatorCreatedAt = timeProvider.now().toEpochMilliseconds(),
                authenticatorExpiresAt = 0,
                walletType = WalletType.UserWallet,
                username = "",
                pkceCodeVerifier = codeVerifier,
                salt = salt,
                status = SessionStatus.Pending,
            )
            sessionStore.save(pendingSession)

            val oauthResult = oauthLauncher.launch(loginUrl, config.redirectScheme)

            return when (oauthResult) {
                is OAuthResult.Success -> {
                    val params = oauthResult.params
                    val code = params["code"]
                        ?: return ConnectResult.Error(IllegalStateException("Missing 'code' in redirect"))
                    val state = params["state"]
                        ?: return ConnectResult.Error(IllegalStateException("Missing 'state' in redirect"))

                    // Validate state matches sessionId
                    if (state != sessionId) {
                        return ConnectResult.Error(IllegalStateException("State mismatch: expected=$sessionId, got=$state"))
                    }

                    // Phase 5: Exchange auth code for tokens
                    val tokenExchange = TokenExchange(http)
                    val tokenResponse = tokenExchange.exchangeAuthCode(
                        authApiBaseUrl = config.authApiBaseUrl,
                        clientId = config.appId,
                        redirectUri = config.redirectUri,
                        code = code,
                        codeVerifier = codeVerifier,
                    )

                    val bearerToken = "${tokenResponse.token_type} ${tokenResponse.access_token}"

                    // Phase 6: Decode access token for user ID and OIDC token
                    val decoded = Auth2Token.decode(tokenResponse.access_token)

                    // Promote pending key to active
                    p256.delete(P256KeyStoreTags.ACTIVE)
                    // On iOS/Android the key was stored under PENDING; now "move" it to ACTIVE
                    // Since P256KeyStoreProvider doesn't have move(), re-generate under ACTIVE
                    // Actually, just keep using PENDING tag and rename conceptually
                    // For simplicity, we'll sign with PENDING tag and swap references

                    // Phase 7: Create OIDC stamper and set Authorization header
                    val stamperInstance = OidcStamper(
                        p256KeyStore = p256,
                        keyTag = P256KeyStoreTags.PENDING, // will be active once confirmed
                        timeProvider = timeProvider,
                        tokenExchange = tokenExchange,
                        authApiBaseUrl = config.authApiBaseUrl,
                        clientId = config.appId,
                        redirectUri = config.redirectUri,
                        initialAuth2Token = decoded.auth2Token,
                        initialAccessToken = tokenResponse.access_token,
                        initialRefreshToken = tokenResponse.refresh_token,
                        initialTokenExpiresAt = decoded.expiresAt,
                        onTokensRefreshed = { newBearerToken, newRefreshToken, newExpiresAt ->
                            persistRefreshedTokens(newBearerToken, newRefreshToken, newExpiresAt)
                        },
                    )
                    this.oidcStamper = stamperInstance
                    client.stamperOverride = stamperInstance
                    client.setAuthorizationHeader(bearerToken)

                    // Phase 8: Get/create organization
                    val p256PubKeyB64 = publicKey.toBase64Url()
                    val orgResult = client.callWithStamper(
                        method = "getOrCreatePhantomOrganization",
                        params = buildJsonObject { put("publicKey", p256PubKeyB64) },
                        overrideStamper = stamperInstance,
                    )
                    val organizationId = orgResult.jsonObject["organizationId"]?.jsonPrimitive?.content
                        ?: throw IllegalStateException("Missing organizationId")

                    // Phase 9: Check pending migrations
                    try {
                        val migrationsResult = client.callWithStamper(
                            method = "listPendingMigrations",
                            params = buildJsonObject { put("organizationId", organizationId) },
                            overrideStamper = stamperInstance,
                        )
                        val migrations = migrationsResult.jsonObject["pendingMigrations"]?.jsonArray
                        if (!migrations.isNullOrEmpty()) {
                            for (migration in migrations) {
                                val migrationId = migration.jsonObject["migrationId"]?.jsonPrimitive?.content
                                if (migrationId != null) {
                                    client.callWithStamper(
                                        method = "completeWalletTransfer",
                                        params = buildJsonObject {
                                            put("organizationId", organizationId)
                                            put("migrationId", migrationId)
                                        },
                                        overrideStamper = stamperInstance,
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        SdkLogger.warn(TAG, "Migration check failed: ${e.message}")
                    }

                    // Phase 10: Get or create wallet
                    val walletId: String
                    val accountDerivationIndex: Int

                    if (decoded.wallet != null) {
                        // Server pre-selected a wallet via access token audience
                        walletId = decoded.wallet.walletId
                        accountDerivationIndex = decoded.wallet.derivationIndex
                        SdkLogger.info(TAG, "Using pre-selected wallet: $walletId (index=$accountDerivationIndex)")
                    } else {
                        // Create wallet with tag using canonical derivation info
                        accountDerivationIndex = 0
                        val walletResult = client.callWithStamper(
                            method = "getOrCreateWalletWithTag",
                            params = buildJsonObject {
                                put("organizationId", organizationId)
                                put("walletName", "App Wallet")
                                put("tag", config.appId)
                                putJsonArray("accounts") {
                                    // Canonical DAPP_WALLET_DERIVATIONS per upstream
                                    add(buildJsonObject {
                                        put("derivationPath", Chain.Solana.derivationPath(0))
                                        put("curve", Chain.Solana.curve)
                                        put("addressFormat", Chain.Solana.addressFormat)
                                    })
                                    add(buildJsonObject {
                                        put("derivationPath", Chain.Ethereum.derivationPath(0))
                                        put("curve", Chain.Ethereum.curve)
                                        put("addressFormat", Chain.Ethereum.addressFormat)
                                    })
                                    add(buildJsonObject {
                                        put("derivationPath", Chain.Bitcoin.derivationPath(0))
                                        put("curve", Chain.Bitcoin.curve)
                                        put("addressFormat", Chain.Bitcoin.addressFormat)
                                    })
                                    add(buildJsonObject {
                                        put("derivationPath", Chain.Sui.derivationPath(0))
                                        put("curve", Chain.Sui.curve)
                                        put("addressFormat", Chain.Sui.addressFormat)
                                    })
                                }
                                put("mnemonicLength", 24)
                            },
                            overrideStamper = stamperInstance,
                        )
                        walletId = walletResult.jsonObject["walletId"]?.jsonPrimitive?.content
                            ?: throw IllegalStateException("Missing walletId")
                    }

                    // Phase 11: Fetch addresses
                    val addresses = try {
                        val derivPaths = config.chains.map { chain ->
                            chain to chain.derivationPath(accountDerivationIndex)
                        }
                        val allPaths = derivPaths.map { it.second }
                        val accountsResult = client.callWithStamper(
                            method = "getAccounts",
                            params = buildJsonObject {
                                put("organizationId", organizationId)
                                put("walletId", walletId)
                                putJsonArray("accounts") { allPaths.forEach { add(it) } }
                            },
                            overrideStamper = stamperInstance,
                        )
                        parseAddresses(accountsResult, derivPaths)
                    } catch (e: Exception) {
                        SdkLogger.warn(TAG, "Failed to fetch addresses: ${e.message}")
                        emptyList()
                    }

                    val now = timeProvider.now()
                    val session = PhantomSession(
                        walletId = walletId,
                        organizationId = organizationId,
                        addresses = addresses,
                        providerId = provider.id,
                        accountDerivationIndex = accountDerivationIndex,
                        authUserId = decoded.userId,
                        sessionId = sessionId,
                        expiresAt = now.toEpochMilliseconds() + AUTHENTICATOR_TTL.inWholeMilliseconds,
                        authenticatorCreatedAt = now.toEpochMilliseconds(),
                        authenticatorExpiresAt = now.toEpochMilliseconds() + AUTHENTICATOR_TTL.inWholeMilliseconds,
                        walletType = WalletType.UserWallet,
                        username = "user-${Uuid.random()}",
                        bearerToken = bearerToken,
                        refreshToken = tokenResponse.refresh_token,
                        tokenExpiresAt = decoded.expiresAt,
                        pkceCodeVerifier = codeVerifier,
                        salt = salt,
                        status = SessionStatus.Completed,
                    )

                    sessionStore.save(session)
                    persistShouldClear(false)
                    SdkLogger.info(TAG, "Auth2 connect succeeded for provider=${provider.id}")
                    _events.tryEmit(PhantomEvent.Connected(session, "social"))
                    ConnectResult.Success(session)
                }

                is OAuthResult.Cancelled -> {
                    SdkLogger.info(TAG, "Connect cancelled: ${oauthResult.reason}")
                    ConnectResult.Cancelled(oauthResult.reason)
                }
                is OAuthResult.Error -> {
                    SdkLogger.error(TAG, "Connect failed: ${oauthResult.cause.message}")
                    _events.tryEmit(PhantomEvent.ConnectError(oauthResult.cause, "social"))
                    ConnectResult.Error(oauthResult.cause)
                }
            }
        } catch (e: Exception) {
            SdkLogger.error(TAG, "Auth2 connect failed: ${e.message}")
            _events.tryEmit(PhantomEvent.ConnectError(e, "social"))
            return ConnectResult.Error(e)
        }
    }

    /**
     * Legacy Ed25519 connect flow (deprecated but kept for backward compatibility).
     */
    @OptIn(ExperimentalUuidApi::class)
    private suspend fun connectWithLegacyFlow(provider: AuthProvider): ConnectResult {
        // Phase 1: Clear old keys and generate a fresh keypair
        keyStore.delete(KeyStoreTags.ACTIVE)
        keyStore.delete(KeyStoreTags.PENDING)
        val publicKey = stamper.ensureKeyPair()
        val publicKeyBase58 = publicKey.toBase58()

        val sessionId = Uuid.random().toString()
        val platform = getPlatform()

        val loginUrl = buildLegacyLoginUrl(
            publicKey = publicKeyBase58,
            provider = provider,
            sessionId = sessionId,
            platform = platform,
        )

        // Phase 2: Launch OAuth and wait for redirect
        val oauthResult = oauthLauncher.launch(loginUrl, config.redirectScheme)

        return when (oauthResult) {
            is OAuthResult.Success -> {
                val params = oauthResult.params
                val walletId = params["wallet_id"]
                    ?: return ConnectResult.Error(IllegalStateException("Missing wallet_id in redirect"))
                val organizationId = params["organization_id"]
                    ?: return ConnectResult.Error(IllegalStateException("Missing organization_id in redirect"))
                val selectedAccountIndex = params["selected_account_index"]?.toIntOrNull() ?: 0
                val expiresInMs = params["expires_in_ms"]?.toLongOrNull() ?: AUTHENTICATOR_TTL.inWholeMilliseconds
                val authUserId = params["auth_user_id"]

                val now = timeProvider.now()
                val username = "user-${Uuid.random()}"

                val session = PhantomSession(
                    walletId = walletId,
                    organizationId = organizationId,
                    providerId = provider.id,
                    accountDerivationIndex = selectedAccountIndex,
                    authUserId = authUserId,
                    sessionId = sessionId,
                    expiresAt = now.toEpochMilliseconds() + expiresInMs,
                    authenticatorCreatedAt = now.toEpochMilliseconds(),
                    authenticatorExpiresAt = now.toEpochMilliseconds() + expiresInMs,
                    walletType = WalletType.UserWallet,
                    username = username,
                )

                // Fetch accounts for all configured chains
                val sessionWithAddresses = try {
                    val addresses = fetchAddresses(
                        organizationId = organizationId,
                        walletId = walletId,
                        accountIndex = selectedAccountIndex,
                        authUserId = authUserId,
                    )
                    session.copy(addresses = addresses)
                } catch (e: Exception) {
                    SdkLogger.warn(TAG, "Failed to fetch addresses: ${e.message}")
                    session
                }

                sessionStore.save(sessionWithAddresses)
                SdkLogger.info(TAG, "Connect succeeded for provider=${provider.id}")
                _events.tryEmit(PhantomEvent.Connected(sessionWithAddresses, "social"))
                ConnectResult.Success(sessionWithAddresses)
            }

            is OAuthResult.Cancelled -> {
                SdkLogger.info(TAG, "Connect cancelled: ${oauthResult.reason}")
                ConnectResult.Cancelled(oauthResult.reason)
            }
            is OAuthResult.Error -> {
                SdkLogger.error(TAG, "Connect failed: ${oauthResult.cause.message}")
                _events.tryEmit(PhantomEvent.ConnectError(oauthResult.cause, "social"))
                ConnectResult.Error(oauthResult.cause)
            }
        }
    }

    /**
     * Connect via an external wallet (e.g. Phantom app).
     *
     * Pure deeplink connect — no KMS involvement. The wallet app is used for
     * every subsequent signing operation via deeplinks.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun connectWithExternalWallet(connector: WalletConnector): ConnectResult {
        SdkLogger.info(TAG, "Connect started with external wallet connector=${connector.id}")
        _events.tryEmit(PhantomEvent.ConnectStart(connector.id, "wallet"))

        val connectResult = connector.connect()
        val walletPublicKeyBase58 = when (connectResult) {
            is WalletConnectorResult.Connected -> connectResult.publicKeyBase58
            is WalletConnectorResult.Cancelled -> {
                SdkLogger.info(TAG, "External wallet connect cancelled: ${connectResult.reason}")
                return ConnectResult.Cancelled(connectResult.reason)
            }
            is WalletConnectorResult.Error -> {
                SdkLogger.error(TAG, "External wallet connect failed: ${connectResult.cause.message}")
                _events.tryEmit(PhantomEvent.ConnectError(connectResult.cause, "wallet"))
                return ConnectResult.Error(connectResult.cause)
            }
        }

        val now = timeProvider.now()
        val sessionId = Uuid.random().toString()

        val session = PhantomSession(
            walletId = "",
            organizationId = "",
            providerId = connector.id,
            accountDerivationIndex = 0,
            authUserId = null,
            sessionId = sessionId,
            expiresAt = 0L,
            authenticatorCreatedAt = now.toEpochMilliseconds(),
            authenticatorExpiresAt = 0L,
            walletType = WalletType.DeeplinkWallet,
            username = "",
            addresses = listOf(
                WalletAddress(
                    chainId = Chain.Solana.id,
                    address = walletPublicKeyBase58,
                    derivationPath = "",
                ),
            ),
        )

        val connectorState = connector.exportState()
        val sessionWithState = session.copy(connectorState = connectorState)

        sessionStore.save(sessionWithState)
        SdkLogger.info(TAG, "External wallet connect succeeded for connector=${connector.id}")
        _events.tryEmit(PhantomEvent.Connected(sessionWithState, "wallet"))
        return ConnectResult.Success(sessionWithState)
    }

    /**
     * Create an app wallet programmatically (no browser).
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun createAppWallet(): ConnectResult {
        keyStore.delete(KeyStoreTags.ACTIVE)
        keyStore.delete(KeyStoreTags.PENDING)
        val publicKey = stamper.ensureKeyPair()

        val username = "user-${Uuid.random()}"
        val sessionId = Uuid.random().toString()

        return try {
            val orgResult = client.createOrganization(
                organizationName = "phantom-kmp-${Uuid.random()}",
                username = username,
                publicKeyBytes = publicKey,
            )
            val organizationId = orgResult.jsonObject["organizationId"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing organizationId in createOrganization response")

            // Create wallet with all configured chain derivation paths
            val derivationPaths = config.chains.map { it.derivationPath(0) }
            val walletResult = client.createWallet(
                organizationId = organizationId,
                walletName = "Default Wallet",
                accounts = derivationPaths,
            )
            val walletId = walletResult.jsonObject["walletId"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing walletId in createWallet response")

            val now = timeProvider.now()
            val expiresInMs = AUTHENTICATOR_TTL.inWholeMilliseconds

            val session = PhantomSession(
                walletId = walletId,
                organizationId = organizationId,
                providerId = "device",
                accountDerivationIndex = 0,
                authUserId = null,
                sessionId = sessionId,
                expiresAt = now.toEpochMilliseconds() + expiresInMs,
                authenticatorCreatedAt = now.toEpochMilliseconds(),
                authenticatorExpiresAt = now.toEpochMilliseconds() + expiresInMs,
                walletType = WalletType.AppWallet,
                username = username,
            )

            sessionStore.save(session)
            _events.tryEmit(PhantomEvent.Connected(session, "appWallet"))
            ConnectResult.Success(session)
        } catch (e: Exception) {
            _events.tryEmit(PhantomEvent.ConnectError(e, "appWallet"))
            ConnectResult.Error(e)
        }
    }

    /** Load the current session, renewing the authenticator if needed. */
    suspend fun getSession(): PhantomSession? {
        val session = sessionStore.load() ?: return null

        // Session validation: clear pending sessions with no active OAuth redirect
        if (session.status == SessionStatus.Pending) {
            if (session.walletId.isEmpty() && session.organizationId.isEmpty()) {
                SdkLogger.info(TAG, "Clearing stale pending session")
                sessionStore.clear()
                return null
            }
        }

        // Session validation: clear sessions missing required fields
        if (session.status == SessionStatus.Completed &&
            session.walletType != WalletType.DeeplinkWallet &&
            (session.walletId.isEmpty() || session.organizationId.isEmpty())
        ) {
            SdkLogger.info(TAG, "Clearing invalid session (missing walletId or organizationId)")
            sessionStore.clear()
            return null
        }

        // Deeplink wallet sessions don't use KMS — skip renewal and address backfill.
        // Restore connector crypto state so signing works immediately after restart.
        if (session.walletType == WalletType.DeeplinkWallet) {
            val state = session.connectorState
            if (state != null) {
                val connector = findConnector(session.providerId)
                connector?.restoreState(state)
            }
            return session
        }

        // For auth2 sessions with bearer token, restore the Authorization header and OIDC stamper
        if (session.bearerToken != null) {
            client.setAuthorizationHeader(session.bearerToken)
            restoreAuth2Stamper(session)
            // If stamper restoration failed (P-256 key lost), session was cleared
            if (oidcStamper == null && p256KeyStore != null) {
                return null
            }
            if (oidcStamper != null) {
                client.stamperOverride = oidcStamper
            }
        }

        val renewed = renewAuthenticatorIfNeeded(session)

        // Backfill addresses if missing
        if (renewed.addresses.isEmpty()) {
            val backfilled = try {
                val addresses = fetchAddresses(
                    organizationId = renewed.organizationId,
                    walletId = renewed.walletId,
                    accountIndex = renewed.accountDerivationIndex,
                    authUserId = renewed.authUserId,
                )
                renewed.copy(addresses = addresses)
            } catch (e: Exception) {
                SdkLogger.warn(TAG, "Failed to backfill addresses: ${e.message}")
                renewed
            }
            if (backfilled.addresses.isNotEmpty()) {
                sessionStore.save(backfilled)
            }
            return backfilled
        }

        return renewed
    }

    /** Clear session and keys. Disconnects from wallet app for deeplink sessions. */
    suspend fun logout(shouldClear: Boolean = true) {
        disconnect(shouldClear, "logout")
    }

    /**
     * Internal disconnect — clears session and optionally sets the clear-previous-session flag.
     * Used by logout (shouldClear=true) and auto-disconnect on auth errors (shouldClear=false).
     */
    private suspend fun disconnect(shouldClear: Boolean, source: String) {
        val session = sessionStore.load()
        if (session?.walletType == WalletType.DeeplinkWallet) {
            val connector = findConnector(session.providerId)
            if (connector != null) {
                try {
                    session.connectorState?.let { connector.restoreState(it) }
                    connector.disconnect()
                } catch (e: Exception) {
                    SdkLogger.warn(TAG, "Failed to disconnect wallet connector: ${e.message}")
                }
            }
        }
        keyStore.delete(KeyStoreTags.ACTIVE)
        keyStore.delete(KeyStoreTags.PENDING)
        p256KeyStore?.delete(P256KeyStoreTags.ACTIVE)
        p256KeyStore?.delete(P256KeyStoreTags.PENDING)
        oidcStamper?.clear()
        oidcStamper = null
        client.stamperOverride = null
        client.setAuthorizationHeader(null)
        sessionStore.clear()
        persistShouldClear(shouldClear)
        _events.tryEmit(PhantomEvent.Disconnected(source))
    }

    /**
     * Check if an exception is an auth error (401/403) and auto-disconnect if so.
     * Re-throws the original exception after disconnecting.
     */
    private suspend fun handleSigningError(e: Exception) {
        val isAuthError = when (e) {
            is Auth2TokenExpiredError -> true
            is io.ktor.client.plugins.ClientRequestException -> {
                isAuthenticationError(e.response.status.value)
            }
            else -> false
        }
        if (isAuthError) {
            SdkLogger.warn(TAG, "Auth error detected, auto-disconnecting: ${e.message}")
            disconnect(shouldClear = false, source = "auth-error")
        }
    }

    /** Sign a UTF-8 message using the current session. */
    suspend fun signMessage(message: String, chain: Chain = Chain.Solana): String {
        val session = requireSession()
        if (session.walletType == WalletType.DeeplinkWallet) {
            require(chain == Chain.Solana) { "Deeplink wallet only supports Solana" }
            val connector = findConnector(session.providerId)
                ?: throw IllegalStateException("No connector for ${session.providerId}")
            return when (val r = connector.signMessage(message)) {
                is WalletSignResult.Success -> r.signature
                is WalletSignResult.Error -> throw r.cause
            }
        }
        val derivationPath = resolveDerivationPath(session, chain)
        try {
            val result = client.signUtf8Message(
                organizationId = session.organizationId,
                walletId = session.walletId,
                message = message,
                chain = chain,
                derivationPath = derivationPath,
                authUserId = session.authUserId,
            )
            return result.jsonObject["signature"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing signature in response")
        } catch (e: Exception) {
            handleSigningError(e)
            throw e
        }
    }

    /** Sign a transaction without broadcasting. */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun signTransaction(transactionBase64: String, chain: Chain = Chain.Solana): String {
        val session = requireSession()
        if (session.walletType == WalletType.DeeplinkWallet) {
            require(chain == Chain.Solana) { "Deeplink wallet only supports Solana" }
            val connector = findConnector(session.providerId)
                ?: throw IllegalStateException("No connector for ${session.providerId}")
            val txBase58 = Base64.decode(transactionBase64).toBase58()
            return when (val r = connector.signTransaction(txBase58)) {
                is WalletSignResult.Success -> r.signature
                is WalletSignResult.Error -> throw r.cause
            }
        }
        val derivationPath = resolveDerivationPath(session, chain)
        try {
            val result = client.signTransaction(
                organizationId = session.organizationId,
                walletId = session.walletId,
                transactionBase64 = transactionBase64,
                chain = chain,
                derivationPath = derivationPath,
                authUserId = session.authUserId,
            )
            return result.jsonObject["signedTransaction"]?.jsonPrimitive?.content
                ?: result.jsonObject["signature"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing signed transaction in response")
        } catch (e: Exception) {
            handleSigningError(e)
            throw e
        }
    }

    /** Sign and submit a transaction. */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun signAndSendTransaction(transactionBase64: String, chain: Chain = Chain.Solana): String {
        val session = requireSession()
        if (session.walletType == WalletType.DeeplinkWallet) {
            require(chain == Chain.Solana) { "Deeplink wallet only supports Solana" }
            val connector = findConnector(session.providerId)
                ?: throw IllegalStateException("No connector for ${session.providerId}")
            val rpc = solanaRpcClient
                ?: throw IllegalStateException("SolanaRpcClient required for deeplink signAndSendTransaction")

            // Sign via signTransaction deeplink (signAndSendTransaction deeplink is deprecated)
            val txBase58 = Base64.decode(transactionBase64).toBase58()
            val signedTxBase58 = when (val r = connector.signTransaction(txBase58)) {
                is WalletSignResult.Success -> r.signature
                is WalletSignResult.Error -> throw r.cause
            }

            // Submit the signed transaction to Solana RPC
            val signedTxBase64 = Base64.encode(signedTxBase58.fromBase58())
            return rpc.sendTransaction(signedTxBase64)
        }
        val derivationPath = resolveDerivationPath(session, chain)
        val account = session.address(chain)

        // User wallets on Solana require the /prepare step for spending limits
        val transactionForSigning = if (
            session.walletType == WalletType.UserWallet &&
            chain == Chain.Solana &&
            account != null
        ) {
            // Only send authenticator public key for legacy (Ed25519) sessions, not OIDC
            val isAuth2Session = session.bearerToken != null
            val authPubKey = if (!isAuth2Session) {
                try { stamper.getPublicKeyBase58() } catch (_: Exception) { null }
            } else {
                null
            }
            try {
                client.prepare(
                    transactionBase64 = transactionBase64,
                    organizationId = session.organizationId,
                    chain = chain,
                    network = config.network,
                    account = account,
                    authenticatorPublicKey = authPubKey,
                    xRpcMethod = "signAndSendTransaction",
                )
            } catch (e: SpendingLimitError) {
                _events.tryEmit(PhantomEvent.SpendingLimitReached(e))
                throw e
            }
        } else {
            transactionBase64
        }

        try {
        val response = client.signAndSubmitTransaction(
            organizationId = session.organizationId,
            walletId = session.walletId,
            transactionBase64 = transactionForSigning,
            chain = chain,
            derivationPath = derivationPath,
            network = config.network,
            account = account,
            authUserId = session.authUserId,
        )

        // Extract tx hash from rpc_submission_result (top-level field, not inside result)
        // Can be: { result: "txSignature" } or { result: { result: "txSignature" } }
        val submissionResult = response.rpcSubmissionResult
        if (submissionResult is JsonObject) {
            val error = submissionResult["error"]
            if (error != null) {
                val errorMsg = when (error) {
                    is JsonObject -> error["data"]?.jsonPrimitive?.content
                        ?: error["message"]?.jsonPrimitive?.content
                        ?: error.toString()
                    else -> error.toString()
                }
                throw IllegalStateException("Transaction broadcast failed: $errorMsg")
            }
            val envelope = submissionResult["result"]
            if (envelope != null) {
                // Direct string: { result: "txSignature" }
                if (envelope is JsonPrimitive && envelope.isString) {
                    return envelope.content
                }
                // Nested: { result: { result: "txSignature" } }
                if (envelope is JsonObject) {
                    envelope["result"]?.jsonPrimitive?.content?.let { return it }
                    envelope["error"]?.let {
                        throw IllegalStateException("Transaction broadcast failed: $it")
                    }
                }
            }
        }

        // Fallback: look in result object
        val result = response.result
        return result?.jsonObject?.get("transactionHash")?.jsonPrimitive?.content
            ?: result?.jsonObject?.get("signature")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing transaction hash in response")
        } catch (e: Exception) {
            handleSigningError(e)
            throw e
        }
    }

    /** Ethereum personal_sign (EIP-191). */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun signPersonalMessage(message: String): String {
        val session = requireSession()
        if (session.walletType == WalletType.DeeplinkWallet) {
            throw UnsupportedOperationException("Deeplink wallet does not support Ethereum signing")
        }
        val derivationPath = resolveDerivationPath(session, Chain.Ethereum)
        SdkLogger.debug(TAG, "signPersonalMessage on Ethereum")

        // Normalize hex-encoded messages (common in personal_sign flows)
        val normalizedMessage = if (message.startsWith("0x") || message.startsWith("0X")) {
            try {
                val hex = message.removePrefix("0x").removePrefix("0X")
                val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                bytes.decodeToString()
            } catch (_: Exception) {
                message // If hex decode fails, use original
            }
        } else {
            message
        }

        try {
            val result = client.signPersonalMessage(
                organizationId = session.organizationId,
                walletId = session.walletId,
                message = normalizedMessage,
                derivationPath = derivationPath,
                authUserId = session.authUserId,
            )
            return result.jsonObject["signature"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing signature in response")
        } catch (e: Exception) {
            handleSigningError(e)
            throw e
        }
    }

    /** Ethereum signTypedData_v4 (EIP-712). */
    suspend fun signTypedData(typedDataJson: String): String {
        val session = requireSession()
        if (session.walletType == WalletType.DeeplinkWallet) {
            throw UnsupportedOperationException("Deeplink wallet does not support Ethereum signing")
        }
        val derivationPath = resolveDerivationPath(session, Chain.Ethereum)
        SdkLogger.debug(TAG, "signTypedData on Ethereum")
        try {
            val result = client.signTypedData(
                organizationId = session.organizationId,
                walletId = session.walletId,
                typedDataJson = typedDataJson,
                derivationPath = derivationPath,
                authUserId = session.authUserId,
            )
            return result.jsonObject["signature"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing signature in response")
        } catch (e: Exception) {
            handleSigningError(e)
            throw e
        }
    }

    /** Batch sign multiple transactions in a single call. */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun signAllTransactions(transactionsBase64: List<String>, chain: Chain = Chain.Solana): List<String> {
        val session = requireSession()
        if (session.walletType == WalletType.DeeplinkWallet) {
            require(chain == Chain.Solana) { "Deeplink wallet only supports Solana" }
            val connector = findConnector(session.providerId)
                ?: throw IllegalStateException("No connector for ${session.providerId}")
            val txsBase58 = transactionsBase64.map { Base64.decode(it).toBase58() }
            return when (val r = connector.signAllTransactions(txsBase58)) {
                is WalletSignAllResult.Success -> r.signatures
                is WalletSignAllResult.Error -> throw r.cause
            }
        }
        val derivationPath = resolveDerivationPath(session, chain)
        SdkLogger.debug(TAG, "signAllTransactions count=${transactionsBase64.size} chain=${chain.id}")
        try {
            val result = client.signAllTransactions(
                organizationId = session.organizationId,
                walletId = session.walletId,
                transactionsBase64 = transactionsBase64,
                chain = chain,
                derivationPath = derivationPath,
                authUserId = session.authUserId,
            )
            val signedArray = result.jsonObject["signedTransactions"]?.jsonArray
                ?: throw IllegalStateException("Missing signedTransactions in response")
            return signedArray.map { it.jsonPrimitive.content }
        } catch (e: Exception) {
            handleSigningError(e)
            throw e
        }
    }

    /** Batch sign and submit multiple transactions (parallel, matching upstream Promise.all). */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun signAndSendAllTransactions(transactionsBase64: List<String>, chain: Chain = Chain.Solana): List<String> {
        return coroutineScope {
            transactionsBase64.map { tx ->
                async { signAndSendTransaction(tx, chain) }
            }.awaitAll()
        }
    }

    // ── Private Helpers ──

    /**
     * Restore the OIDC stamper from a persisted auth2 session.
     * Called during getSession() and autoConnect().
     */
    private suspend fun restoreAuth2Stamper(session: PhantomSession) {
        if (oidcStamper != null) return // already restored

        val p256 = p256KeyStore ?: return
        val http = httpClient ?: return
        val bearer = session.bearerToken ?: return

        // Verify P-256 key still exists
        val hasKey = p256.exists(P256KeyStoreTags.PENDING) || p256.exists(P256KeyStoreTags.ACTIVE)
        if (!hasKey) {
            SdkLogger.warn(TAG, "P-256 key lost — clearing invalid auth2 session")
            sessionStore.clear()
            return
        }

        val keyTag = if (p256.exists(P256KeyStoreTags.PENDING)) P256KeyStoreTags.PENDING else P256KeyStoreTags.ACTIVE

        // Strip "Bearer " prefix to get the raw access token
        val accessToken = bearer.removePrefix("Bearer ").removePrefix("bearer ")

        try {
            val decoded = Auth2Token.decode(accessToken)
            val tokenExchange = TokenExchange(http)

            oidcStamper = OidcStamper(
                p256KeyStore = p256,
                keyTag = keyTag,
                timeProvider = timeProvider,
                tokenExchange = tokenExchange,
                authApiBaseUrl = config.authApiBaseUrl,
                clientId = config.appId,
                redirectUri = config.redirectUri,
                initialAuth2Token = decoded.auth2Token,
                initialAccessToken = accessToken,
                initialRefreshToken = session.refreshToken,
                initialTokenExpiresAt = decoded.expiresAt,
                onTokensRefreshed = { newBearerToken, newRefreshToken, newExpiresAt ->
                    persistRefreshedTokens(newBearerToken, newRefreshToken, newExpiresAt)
                },
            )
            SdkLogger.info(TAG, "Restored OIDC stamper for auth2 session")
        } catch (e: Exception) {
            SdkLogger.warn(TAG, "Failed to restore OIDC stamper: ${e.message}")
        }
    }

    /**
     * Persist refreshed tokens back to the session store so they survive app restart.
     */
    private suspend fun persistRefreshedTokens(bearerToken: String, refreshToken: String?, tokenExpiresAt: Long) {
        try {
            val session = sessionStore.load() ?: return
            val updated = session.copy(
                bearerToken = bearerToken,
                refreshToken = refreshToken ?: session.refreshToken,
                tokenExpiresAt = tokenExpiresAt,
            )
            sessionStore.save(updated)
            client.setAuthorizationHeader(bearerToken)
            SdkLogger.debug(TAG, "Persisted refreshed tokens to session store")
        } catch (e: Exception) {
            SdkLogger.warn(TAG, "Failed to persist refreshed tokens: ${e.message}")
        }
    }

    private suspend fun requireSession(): PhantomSession =
        getSession() ?: throw IllegalStateException("No active session")

    /**
     * Resolve the derivation path for a chain from session addresses,
     * falling back to computing it from the account derivation index.
     */
    private fun resolveDerivationPath(session: PhantomSession, chain: Chain): String {
        return session.addresses
            .firstOrNull { it.chainId == chain.id }
            ?.derivationPath
            ?: chain.derivationPath(session.accountDerivationIndex)
    }

    private suspend fun fetchAddresses(
        organizationId: String,
        walletId: String,
        accountIndex: Int,
        authUserId: String?,
    ): List<WalletAddress> {
        val derivationPaths = config.chains.map { chain ->
            chain to chain.derivationPath(accountIndex)
        }

        val allPaths = derivationPaths.map { it.second }
        val accountsResult = client.getAccounts(
            organizationId = organizationId,
            walletId = walletId,
            accounts = allPaths,
            authUserId = authUserId,
        )

        return parseAddresses(accountsResult, derivationPaths)
    }

    private fun parseAddresses(
        result: JsonElement,
        derivationPaths: List<Pair<Chain, String>>,
    ): List<WalletAddress> {
        val accounts = when (result) {
            is JsonArray -> result
            is JsonObject -> result["accounts"]?.jsonArray ?: return emptyList()
            else -> return emptyList()
        }

        return accounts.mapIndexedNotNull { index, account ->
            val address = account.jsonObject["address"]?.jsonPrimitive?.content
                ?: return@mapIndexedNotNull null
            val (chain, derivationPath) = derivationPaths.getOrElse(index) {
                derivationPaths.first()
            }
            WalletAddress(
                chainId = chain.id,
                address = address,
                derivationPath = derivationPath,
            )
        }
    }

    // ── Authenticator Renewal (Three-Phase Commit) ──
    // NOTE: Renewal is disabled per upstream PR #283. The server no longer supports
    // authenticator rotation. Kept for potential future re-enablement.

    private suspend fun renewAuthenticatorIfNeeded(session: PhantomSession): PhantomSession {
        // Renewal disabled — see upstream PR #283
        return session
    }

    private suspend fun rotateAuthenticator(session: PhantomSession): PhantomSession {
        keyStore.delete(KeyStoreTags.PENDING)
        val pendingPublicKey = keyStore.generateKeyPair(KeyStoreTags.PENDING)

        try {
            client.createAuthenticator(
                organizationId = session.organizationId,
                username = session.username,
                publicKeyBytes = pendingPublicKey,
                replaceExpirable = true,
                authUserId = session.authUserId,
            )

            commitRotation()

            val now = timeProvider.now()
            val updatedSession = session.copy(
                authenticatorCreatedAt = now.toEpochMilliseconds(),
                authenticatorExpiresAt = now.toEpochMilliseconds() + AUTHENTICATOR_TTL.inWholeMilliseconds,
            )
            sessionStore.save(updatedSession)
            return updatedSession
        } catch (e: Exception) {
            rollbackRotation()
            throw e
        }
    }

    private suspend fun commitRotation() {
        keyStore.delete(KeyStoreTags.ACTIVE)
        keyStore.move(KeyStoreTags.PENDING, KeyStoreTags.ACTIVE)
    }

    private suspend fun rollbackRotation() {
        keyStore.delete(KeyStoreTags.PENDING)
    }

    // ── URL Construction ──

    private fun buildLegacyLoginUrl(
        publicKey: String,
        provider: AuthProvider,
        sessionId: String,
        platform: String,
    ): String {
        val params = buildMap {
            put("public_key", publicKey)
            put("app_id", config.appId)
            put("redirect_uri", config.redirectUri)
            put("session_id", sessionId)
            put("provider", provider.id)
            put("clear_previous_session", if (shouldClearPreviousSession) "true" else "false")
            put("allow_refresh", if (shouldClearPreviousSession) "false" else "true")
            put("sdk_version", config.sdkVersion)
            put("sdk_type", sdkType)
            put("platform", platform)
        }
        val queryString = params.entries.joinToString("&") { (k, v) ->
            "$k=${urlEncode(v)}"
        }
        return "${config.loginBaseUrl}/login?$queryString"
    }
}

// Simple URL encoding for query params
private fun urlEncode(value: String): String = buildString {
    for (c in value) {
        when {
            c.isLetterOrDigit() || c in "-_.~" -> append(c)
            c == ' ' -> append("+")
            else -> {
                val bytes = c.toString().encodeToByteArray()
                for (b in bytes) {
                    append('%')
                    append((b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
    }
}
