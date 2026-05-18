package dev.bmcreations.phantom.connect

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import dev.bmcreations.phantom.connect.fakes.*
import dev.bmcreations.phantom.connect.internal.auth.AuthOrchestrator
import dev.bmcreations.phantom.connect.internal.crypto.Ed25519Stamper
import dev.bmcreations.phantom.connect.internal.crypto.KeyStoreTags
import dev.bmcreations.phantom.connect.internal.network.PhantomClient
import dev.bmcreations.phantom.connect.internal.network.SolanaRpcClient
import dev.bmcreations.phantom.connect.internal.platform.sdkType
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalUnsignedTypes::class)
class AuthOrchestratorTest {

    private lateinit var keyStore: FakeEd25519KeyStore
    private lateinit var sessionStore: FakeSessionStore
    private lateinit var oauthLauncher: FakeOAuthLauncher
    private lateinit var timeProvider: FakeTimeProvider
    private val json = Json { ignoreUnknownKeys = true }

    private val config = PhantomSdkConfig(
        appId = "test-app",
        redirectUri = "testapp://phantom-callback",
        baseUrl = "https://api.phantom.app",
    )

    @BeforeTest
    fun setup() = runTest {
        LibsodiumInitializer.initialize()
        keyStore = FakeEd25519KeyStore()
        sessionStore = FakeSessionStore()
        oauthLauncher = FakeOAuthLauncher()
        timeProvider = FakeTimeProvider()
    }

    private fun createOrchestrator(
        mockEngine: MockEngine,
        connectors: List<WalletConnector> = emptyList(),
        solanaRpcEngine: MockEngine? = null,
    ): AuthOrchestrator {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val stamper = Ed25519Stamper(keyStore)
        val client = PhantomClient(httpClient, stamper, config, timeProvider)
        val solanaRpcClient = solanaRpcEngine?.let { engine ->
            val rpcHttpClient = HttpClient(engine) {
                install(ContentNegotiation) { json(json) }
            }
            SolanaRpcClient(rpcHttpClient, config.network)
        }
        return AuthOrchestrator(client, keyStore, sessionStore, oauthLauncher, stamper, config, timeProvider, connectors, solanaRpcClient)
    }

    private fun mockEngineForUserWallet(): MockEngine = MockEngine { request ->
        val body = request.body.toByteArray().decodeToString()
        when {
            body.contains("getAccounts") -> respond(
                content = """{"result": {"accounts": [{"address": "So1anaAddress123"}]}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            else -> respond(
                content = """{"result": {}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
    }

    private fun mockEngineForAppWallet(): MockEngine = MockEngine { request ->
        val body = request.body.toByteArray().decodeToString()
        when {
            body.contains("createOrganization") -> respond(
                content = """{"result": {"organizationId": "org-created-123"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            body.contains("createWallet") -> respond(
                content = """{"result": {"walletId": "wallet-created-456"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            else -> respond(
                content = """{"result": {}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
    }

    // ── User Wallet (OAuth) Tests ──

    @Test
    fun connectWithSocialLaunchesOAuthWithCorrectUrl() = runTest {
        oauthLauncher.succeedWith()
        val orchestrator = createOrchestrator(mockEngineForUserWallet())

        orchestrator.connectWithSocial(AuthProvider.Google)

        assertEquals(1, oauthLauncher.launchCount)
        val url = oauthLauncher.launchedUrls.first()
        assertTrue(url.contains("connect.phantom.app/login"))
        assertTrue(url.contains("provider=google"))
        assertTrue(url.contains("app_id=test-app"))
        assertTrue(url.contains("redirect_uri="))
        assertTrue(url.contains("public_key="))
        assertTrue(url.contains("sdk_type=$sdkType"))
    }

    @Test
    fun connectWithSocialReturnsSuccessOnRedirect() = runTest {
        oauthLauncher.succeedWith(
            walletId = "my-wallet",
            organizationId = "my-org",
            selectedAccountIndex = 1,
        )
        val orchestrator = createOrchestrator(mockEngineForUserWallet())

        val result = orchestrator.connectWithSocial(AuthProvider.Google)

        assertTrue(result is ConnectResult.Success)
        assertEquals("my-wallet", result.session.walletId)
        assertEquals("my-org", result.session.organizationId)
        assertEquals(1, result.session.accountDerivationIndex)
        assertEquals("google", result.session.providerId)
        assertEquals(WalletType.UserWallet, result.session.walletType)
    }

    @Test
    fun connectWithSocialSavesSession() = runTest {
        oauthLauncher.succeedWith()
        val orchestrator = createOrchestrator(mockEngineForUserWallet())

        orchestrator.connectWithSocial(AuthProvider.Apple)

        assertEquals(1, sessionStore.saveCount)
        assertNotNull(sessionStore.stored)
        assertEquals("apple", sessionStore.stored?.providerId)
    }

    @Test
    fun connectWithSocialReturnsCancelledWhenUserDismisses() = runTest {
        oauthLauncher.nextResult = OAuthResult.Cancelled("user dismissed")
        val orchestrator = createOrchestrator(mockEngineForUserWallet())

        val result = orchestrator.connectWithSocial(AuthProvider.Google)

        assertTrue(result is ConnectResult.Cancelled)
        assertEquals("user dismissed", result.reason)
    }

    @Test
    fun connectWithSocialReturnsErrorOnOAuthFailure() = runTest {
        oauthLauncher.nextResult = OAuthResult.Error(RuntimeException("network error"))
        val orchestrator = createOrchestrator(mockEngineForUserWallet())

        val result = orchestrator.connectWithSocial(AuthProvider.Google)

        assertTrue(result is ConnectResult.Error)
        assertEquals("network error", result.cause.message)
    }

    @Test
    fun connectWithSocialClearsOldKeysFirst() = runTest {
        // Pre-populate keys
        keyStore.generateKeyPair(KeyStoreTags.ACTIVE)
        keyStore.generateKeyPair(KeyStoreTags.PENDING)
        val initialGenerateCount = keyStore.generateCount

        oauthLauncher.succeedWith()
        val orchestrator = createOrchestrator(mockEngineForUserWallet())
        orchestrator.connectWithSocial(AuthProvider.Google)

        // Should have generated a fresh key (old ones deleted)
        assertEquals(initialGenerateCount + 1, keyStore.generateCount)
    }

    @Test
    fun connectWithSocialSetsAuthUserId() = runTest {
        oauthLauncher.succeedWith(authUserId = "auth-user-xyz")
        val orchestrator = createOrchestrator(mockEngineForUserWallet())

        val result = orchestrator.connectWithSocial(AuthProvider.Google) as ConnectResult.Success
        assertEquals("auth-user-xyz", result.session.authUserId)
    }

    // ── App Wallet Tests ──

    @Test
    fun createAppWalletSucceeds() = runTest {
        val orchestrator = createOrchestrator(mockEngineForAppWallet())

        val result = orchestrator.createAppWallet()

        assertTrue(result is ConnectResult.Success)
        assertEquals("wallet-created-456", result.session.walletId)
        assertEquals("org-created-123", result.session.organizationId)
        assertEquals("device", result.session.providerId)
        assertEquals(WalletType.AppWallet, result.session.walletType)
        assertNull(result.session.authUserId)
    }

    @Test
    fun createAppWalletSavesSession() = runTest {
        val orchestrator = createOrchestrator(mockEngineForAppWallet())

        orchestrator.createAppWallet()

        assertEquals(1, sessionStore.saveCount)
        assertNotNull(sessionStore.stored)
    }

    @Test
    fun createAppWalletReturnsErrorOnApiFailure() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"error": {"message": "server down"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val orchestrator = createOrchestrator(mockEngine)

        val result = orchestrator.createAppWallet()

        assertTrue(result is ConnectResult.Error)
    }

    // ── Session Lifecycle Tests ──

    @Test
    fun getSessionReturnsNullWhenNoSession() = runTest {
        val orchestrator = createOrchestrator(mockEngineForUserWallet())

        assertNull(orchestrator.getSession())
    }

    @Test
    fun getSessionReturnsStoredSession() = runTest {
        oauthLauncher.succeedWith()
        val orchestrator = createOrchestrator(mockEngineForUserWallet())
        orchestrator.connectWithSocial(AuthProvider.Google)

        val session = orchestrator.getSession()
        assertNotNull(session)
        assertEquals("test-wallet-id", session.walletId)
    }

    @Test
    fun logoutClearsSessionAndKeys() = runTest {
        oauthLauncher.succeedWith()
        val orchestrator = createOrchestrator(mockEngineForUserWallet())
        orchestrator.connectWithSocial(AuthProvider.Google)

        orchestrator.logout()

        assertNull(sessionStore.stored)
        assertEquals(1, sessionStore.clearCount)
        assertFalse(keyStore.exists(KeyStoreTags.ACTIVE))
        assertFalse(keyStore.exists(KeyStoreTags.PENDING))
    }

    // ── Authenticator Renewal Tests ──

    @Test
    fun getSessionDoesNotRenewBeforeWindow() = runTest {
        oauthLauncher.succeedWith(expiresInMs = 31.days.inWholeMilliseconds)
        val orchestrator = createOrchestrator(mockEngineForUserWallet())
        orchestrator.connectWithSocial(AuthProvider.Google)

        // Advance 4 days — still outside 2-day renewal window
        timeProvider.advanceBy(4.days)

        val session = orchestrator.getSession()
        assertNotNull(session)
        // Session timestamps should be unchanged (no renewal occurred)
        assertEquals(sessionStore.stored?.authenticatorCreatedAt, session.authenticatorCreatedAt)
    }

    @Test
    fun getSessionDoesNotRenewAuthenticator() = runTest {
        // Renewal is disabled per upstream PR #283
        oauthLauncher.succeedWith(expiresInMs = 31.days.inWholeMilliseconds)

        val mockEngine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            when {
                body.contains("getAccounts") -> respond(
                    content = """{"result": {"accounts": [{"address": "addr"}]}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond(
                    content = """{"result": {}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }

        val orchestrator = createOrchestrator(mockEngine)
        orchestrator.connectWithSocial(AuthProvider.Google)

        val originalAuthExpiresAt = sessionStore.stored?.authenticatorExpiresAt

        // Advance 29 days — inside the old renewal window but renewal is disabled
        timeProvider.advanceBy(29.days)

        val session = orchestrator.getSession()
        assertNotNull(session)
        // authenticatorExpiresAt should be UNCHANGED (no renewal)
        assertEquals(originalAuthExpiresAt, session.authenticatorExpiresAt)
    }

    // ── Signing Tests ──

    @Test
    fun signPersonalMessageUsesEthereum() = runTest {
        oauthLauncher.succeedWith()
        val mockEngine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            when {
                body.contains("getAccounts") -> respond(
                    content = """{"result": {"accounts": [{"address": "0xEthAddr"}]}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                body.contains("signUtf8Message") -> {
                    assertTrue(body.contains("personal_sign"))
                    assertTrue(body.contains("Secp256k1"))
                    respond(
                        content = """{"result": {"signature": "eth-sig-123"}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respond(
                    content = """{"result": {}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val orchestrator = createOrchestrator(mockEngine)
        orchestrator.connectWithSocial(AuthProvider.Google)

        val sig = orchestrator.signPersonalMessage("Hello Ethereum!")
        assertEquals("eth-sig-123", sig)
    }

    @Test
    fun signTypedDataUsesEthereum() = runTest {
        oauthLauncher.succeedWith()
        val mockEngine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            when {
                body.contains("getAccounts") -> respond(
                    content = """{"result": {"accounts": [{"address": "0xEthAddr"}]}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                body.contains("signTypedData") -> {
                    assertTrue(body.contains("Secp256k1"))
                    respond(
                        content = """{"result": {"signature": "typed-sig-456"}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respond(
                    content = """{"result": {}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val orchestrator = createOrchestrator(mockEngine)
        orchestrator.connectWithSocial(AuthProvider.Google)

        val sig = orchestrator.signTypedData("""{"types":{},"primaryType":"Test","domain":{},"message":{}}""")
        assertEquals("typed-sig-456", sig)
    }

    @Test
    fun signAllTransactionsReturnsList() = runTest {
        oauthLauncher.succeedWith()
        val mockEngine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            when {
                body.contains("getAccounts") -> respond(
                    content = """{"result": {"accounts": [{"address": "SoLAddr"}]}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                body.contains("signAllTransactions") -> respond(
                    content = """{"result": {"signedTransactions": ["signed1==", "signed2=="]}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond(
                    content = """{"result": {}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val orchestrator = createOrchestrator(mockEngine)
        orchestrator.connectWithSocial(AuthProvider.Google)

        val signed = orchestrator.signAllTransactions(listOf("tx1==", "tx2=="))
        assertEquals(listOf("signed1==", "signed2=="), signed)
    }

    // ── External Wallet Connect Tests (Deeplink) ──

    @Test
    fun connectWithExternalWalletSucceeds() = runTest {
        val connector = FakeWalletConnector()
        connector.succeedWith()

        val orchestrator = createOrchestrator(mockEngineForUserWallet(), connectors = listOf(connector))
        val result = orchestrator.connectWithExternalWallet(connector)

        assertTrue(result is ConnectResult.Success)
        val session = result.session
        assertEquals("", session.walletId)
        assertEquals("", session.organizationId)
        assertEquals("fake_wallet", session.providerId)
        assertEquals(WalletType.DeeplinkWallet, session.walletType)
        assertEquals("FakeWalletPubKey123", session.address(Chain.Solana))
        assertEquals(1, connector.connectCount)
    }

    @Test
    fun connectWithExternalWalletCancelled() = runTest {
        val connector = FakeWalletConnector()
        connector.nextConnectResult = WalletConnectorResult.Cancelled("user cancelled")

        val orchestrator = createOrchestrator(mockEngineForUserWallet())
        val result = orchestrator.connectWithExternalWallet(connector)

        assertTrue(result is ConnectResult.Cancelled)
        assertEquals("user cancelled", (result as ConnectResult.Cancelled).reason)
        assertEquals(1, connector.connectCount)
    }

    @Test
    fun connectWithExternalWalletError() = runTest {
        val connector = FakeWalletConnector()
        connector.nextConnectResult = WalletConnectorResult.Error(RuntimeException("app not installed"))

        val orchestrator = createOrchestrator(mockEngineForUserWallet())
        val result = orchestrator.connectWithExternalWallet(connector)

        assertTrue(result is ConnectResult.Error)
        assertEquals("app not installed", (result as ConnectResult.Error).cause.message)
    }

    @Test
    fun connectWithExternalWalletSavesSession() = runTest {
        val connector = FakeWalletConnector()
        connector.succeedWith()

        val orchestrator = createOrchestrator(mockEngineForUserWallet(), connectors = listOf(connector))
        orchestrator.connectWithExternalWallet(connector)

        assertEquals(1, sessionStore.saveCount)
        assertNotNull(sessionStore.stored)
        assertEquals("fake_wallet", sessionStore.stored?.providerId)
        assertEquals(WalletType.DeeplinkWallet, sessionStore.stored?.walletType)
    }

    // ── Deeplink Signing Routing Tests ──

    @Test
    fun signMessageRoutesToConnectorForDeeplinkSession() = runTest {
        val connector = FakeWalletConnector()
        connector.succeedWith()
        connector.nextSignMessageResult = WalletSignResult.Success("deeplink-sig-abc")

        val orchestrator = createOrchestrator(mockEngineForUserWallet(), connectors = listOf(connector))
        orchestrator.connectWithExternalWallet(connector)

        val sig = orchestrator.signMessage("Hello Solana")
        assertEquals("deeplink-sig-abc", sig)
        assertEquals(1, connector.signMessageCount)
    }

    @Test
    fun signTransactionRoutesToConnector() = runTest {
        val connector = FakeWalletConnector()
        connector.succeedWith()
        connector.nextSignTransactionResult = WalletSignResult.Success("signed-tx-base58")

        val orchestrator = createOrchestrator(mockEngineForUserWallet(), connectors = listOf(connector))
        orchestrator.connectWithExternalWallet(connector)

        val sig = orchestrator.signTransaction("dHgxZGF0YQ==") // "tx1data" base64
        assertEquals("signed-tx-base58", sig)
        assertEquals(1, connector.signTransactionCount)
    }

    @Test
    fun signAndSendTransactionSignsThenSubmitsViaRpcForDeeplink() = runTest {
        val connector = FakeWalletConnector()
        connector.succeedWith()
        // signTransaction returns a base58-encoded signed tx (must be valid base58)
        connector.nextSignTransactionResult = WalletSignResult.Success("3AsdoALgZFuq8id")

        // Mock Solana RPC that returns a tx signature
        val solanaRpcEngine = MockEngine {
            respond(
                content = """{"jsonrpc":"2.0","id":1,"result":"5wHu1qwD7q5j2F4pHDn...txSig"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val orchestrator = createOrchestrator(
            mockEngineForUserWallet(),
            connectors = listOf(connector),
            solanaRpcEngine = solanaRpcEngine,
        )
        orchestrator.connectWithExternalWallet(connector)

        val sig = orchestrator.signAndSendTransaction("dHgxZGF0YQ==")
        assertEquals("5wHu1qwD7q5j2F4pHDn...txSig", sig)
        // Should use signTransaction, NOT signAndSendTransaction
        assertEquals(1, connector.signTransactionCount)
        assertEquals(0, connector.signAndSendTransactionCount)
    }

    @Test
    fun signAllTransactionsRoutesToConnector() = runTest {
        val connector = FakeWalletConnector()
        connector.succeedWith()
        connector.nextSignAllTransactionsResult = WalletSignAllResult.Success(listOf("s1", "s2"))

        val orchestrator = createOrchestrator(mockEngineForUserWallet(), connectors = listOf(connector))
        orchestrator.connectWithExternalWallet(connector)

        val signed = orchestrator.signAllTransactions(listOf("dHgx", "dHgy"))
        assertEquals(listOf("s1", "s2"), signed)
        assertEquals(1, connector.signAllTransactionsCount)
    }

    @Test
    fun ethereumSigningThrowsForDeeplinkSession() = runTest {
        val connector = FakeWalletConnector()
        connector.succeedWith()

        val orchestrator = createOrchestrator(mockEngineForUserWallet(), connectors = listOf(connector))
        orchestrator.connectWithExternalWallet(connector)

        assertFailsWith<UnsupportedOperationException> {
            orchestrator.signPersonalMessage("Hello Ethereum")
        }
        assertFailsWith<UnsupportedOperationException> {
            orchestrator.signTypedData("""{"types":{}}""")
        }
    }

    @Test
    fun getSessionSkipsRenewalForDeeplinkSession() = runTest {
        val connector = FakeWalletConnector()
        connector.succeedWith()

        val orchestrator = createOrchestrator(mockEngineForUserWallet(), connectors = listOf(connector))
        orchestrator.connectWithExternalWallet(connector)

        // Deeplink sessions have expiresAt=0, so renewal would normally trigger.
        // But getSession should skip renewal entirely for deeplink sessions.
        val session = orchestrator.getSession()
        assertNotNull(session)
        assertEquals(WalletType.DeeplinkWallet, session.walletType)
    }

    @Test
    fun logoutCallsDisconnectForDeeplinkSession() = runTest {
        val connector = FakeWalletConnector()
        connector.succeedWith()
        connector.exportedState = """{"fake":"state"}"""

        val orchestrator = createOrchestrator(mockEngineForUserWallet(), connectors = listOf(connector))
        orchestrator.connectWithExternalWallet(connector)

        orchestrator.logout()

        assertEquals(1, connector.disconnectCount)
        assertNull(sessionStore.stored)
    }

    @Test
    fun connectWithExternalWalletPersistsConnectorState() = runTest {
        val connector = FakeWalletConnector()
        connector.succeedWith()
        connector.exportedState = """{"dappSecretKey":"abc","sharedSecret":"xyz"}"""

        val orchestrator = createOrchestrator(mockEngineForUserWallet(), connectors = listOf(connector))
        val result = orchestrator.connectWithExternalWallet(connector)

        assertTrue(result is ConnectResult.Success)
        assertEquals("""{"dappSecretKey":"abc","sharedSecret":"xyz"}""", result.session.connectorState)
        assertEquals("""{"dappSecretKey":"abc","sharedSecret":"xyz"}""", sessionStore.stored?.connectorState)
    }

    @Test
    fun getSessionRestoresConnectorStateForDeeplinkSession() = runTest {
        val connector = FakeWalletConnector()
        connector.succeedWith()
        connector.exportedState = """{"fake":"state"}"""

        val orchestrator = createOrchestrator(mockEngineForUserWallet(), connectors = listOf(connector))
        orchestrator.connectWithExternalWallet(connector)

        // Reset restore tracking, then call getSession
        connector.restoreCount = 0
        connector.restoredState = null

        val session = orchestrator.getSession()
        assertNotNull(session)
        assertEquals(1, connector.restoreCount)
        assertEquals("""{"fake":"state"}""", connector.restoredState)
    }

    @Test
    fun getSessionSkipsRestoreWhenNoConnectorState() = runTest {
        val connector = FakeWalletConnector()
        connector.succeedWith()
        // exportedState is null — no connector state to persist

        val orchestrator = createOrchestrator(mockEngineForUserWallet(), connectors = listOf(connector))
        orchestrator.connectWithExternalWallet(connector)

        connector.restoreCount = 0
        val session = orchestrator.getSession()
        assertNotNull(session)
        assertEquals(0, connector.restoreCount)
    }

    @Test
    fun logoutRestoresStateThenDisconnects() = runTest {
        val connector = FakeWalletConnector()
        connector.succeedWith()
        connector.exportedState = """{"crypto":"state"}"""

        val orchestrator = createOrchestrator(mockEngineForUserWallet(), connectors = listOf(connector))
        orchestrator.connectWithExternalWallet(connector)

        // Reset tracking
        connector.restoreCount = 0
        connector.restoredState = null

        orchestrator.logout()

        // Should restore state before disconnecting
        assertEquals(1, connector.restoreCount)
        assertEquals("""{"crypto":"state"}""", connector.restoredState)
        assertEquals(1, connector.disconnectCount)
    }

    // ── Authenticator Renewal Tests ──

    @Test
    fun renewalRollsBackOnFailure() = runTest {
        oauthLauncher.succeedWith(expiresInMs = 31.days.inWholeMilliseconds)

        var callCount = 0
        val mockEngine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            callCount++
            when {
                body.contains("getAccounts") -> respond(
                    content = """{"result": {"accounts": []}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                body.contains("createAuthenticator") -> respond(
                    content = """{"error": {"message": "renewal failed"}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond(
                    content = """{"result": {}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }

        val orchestrator = createOrchestrator(mockEngine)
        orchestrator.connectWithSocial(AuthProvider.Google)

        // Advance into renewal window
        timeProvider.advanceBy(6.days)

        // getSession should still return the old session (renewal failed but rolled back)
        val session = orchestrator.getSession()
        assertNotNull(session)
        // Pending key should be cleaned up
        assertFalse(keyStore.exists(KeyStoreTags.PENDING))
        // Active key should still exist
        assertTrue(keyStore.exists(KeyStoreTags.ACTIVE))
    }
}
