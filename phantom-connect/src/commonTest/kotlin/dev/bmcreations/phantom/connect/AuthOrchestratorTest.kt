package dev.bmcreations.phantom.connect

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import dev.bmcreations.phantom.connect.fakes.*
import dev.bmcreations.phantom.connect.internal.*
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
        redirectScheme = "testapp",
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

    private fun createOrchestrator(mockEngine: MockEngine): AuthOrchestrator {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val stamper = Ed25519Stamper(keyStore)
        val client = PhantomClient(httpClient, stamper, config, timeProvider)
        return AuthOrchestrator(client, keyStore, sessionStore, oauthLauncher, stamper, config, timeProvider)
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
        oauthLauncher.succeedWith(expiresInMs = 7.days.inWholeMilliseconds)
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
    fun getSessionRenewsWithinWindow() = runTest {
        oauthLauncher.succeedWith(expiresInMs = 7.days.inWholeMilliseconds)

        // Mock engine that handles both getAccounts and createAuthenticator
        val mockEngine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            when {
                body.contains("getAccounts") -> respond(
                    content = """{"result": {"accounts": [{"address": "addr"}]}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                body.contains("createAuthenticator") -> respond(
                    content = """{"result": {"authenticatorId": "new-auth"}}""",
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

        // Advance 5.5 days — inside the 2-day renewal window (7 - 5.5 = 1.5 days left)
        timeProvider.advanceBy(5.days + 12.hours)

        val session = orchestrator.getSession()
        assertNotNull(session)
        // authenticatorExpiresAt should be updated
        assertTrue(session.authenticatorExpiresAt > originalAuthExpiresAt!!)
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

    // ── Authenticator Renewal Tests ──

    @Test
    fun renewalRollsBackOnFailure() = runTest {
        oauthLauncher.succeedWith(expiresInMs = 7.days.inWholeMilliseconds)

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
