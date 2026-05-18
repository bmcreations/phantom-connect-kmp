package dev.bmcreations.phantom.connect

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import dev.bmcreations.phantom.connect.fakes.*
import dev.bmcreations.phantom.connect.internal.auth.InMemorySessionStore
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(ExperimentalUnsignedTypes::class)
class PhantomSdkTest {

    private lateinit var keyStore: FakeEd25519KeyStore
    private lateinit var sessionStore: FakeSessionStore
    private lateinit var oauthLauncher: FakeOAuthLauncher
    private lateinit var timeProvider: FakeTimeProvider
    private val json = Json { ignoreUnknownKeys = true }

    private val config = PhantomSdkConfig(
        appId = "test-app",
        redirectUri = "testapp://phantom-callback",
    )

    @BeforeTest
    fun setup() = runTest {
        LibsodiumInitializer.initialize()
        keyStore = FakeEd25519KeyStore()
        sessionStore = FakeSessionStore()
        oauthLauncher = FakeOAuthLauncher()
        timeProvider = FakeTimeProvider()
    }

    private fun createSdk(mockEngine: MockEngine): PhantomSdk {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        return PhantomSdk.createForTesting(
            keyStore = keyStore,
            sessionStore = sessionStore,
            oauthLauncher = oauthLauncher,
            httpClient = httpClient,
            config = config,
            timeProvider = timeProvider,
        )
    }

    private fun defaultMockEngine(): MockEngine = MockEngine { request ->
        val body = request.body.toByteArray().decodeToString()
        when {
            request.url.encodedPath.endsWith("/prepare") -> respond(
                content = """{"transaction": "prepared-tx-base64url"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            body.contains("getAccounts") -> respond(
                content = """{"result": {"accounts": [{"address": "SoLaNaAddr123"}]}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            body.contains("createOrganization") -> respond(
                content = """{"result": {"organizationId": "org-1"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            body.contains("createWallet") -> respond(
                content = """{"result": {"walletId": "wallet-1"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            body.contains("signUtf8Message") -> respond(
                content = """{"result": {"signature": "sig123abc"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            request.headers["X-Rpc-Method"] == "signAndSendTransaction" -> respond(
                content = """{"result": {"transaction": "signedTx"}, "rpc_submission_result": {"result": "txhash456"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
            else -> respond(
                content = """{"result": {}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
    }

    @Test
    fun fullGoogleLoginFlow() = runTest {
        oauthLauncher.succeedWith(
            walletId = "my-wallet",
            organizationId = "my-org",
        )
        val sdk = createSdk(defaultMockEngine())

        val result = sdk.connect(AuthProvider.Google)

        assertTrue(result is ConnectResult.Success)
        assertEquals("my-wallet", result.session.walletId)
        assertEquals("my-org", result.session.organizationId)
        assertEquals("google", result.session.providerId)
    }

    @Test
    fun sessionPersistsAcrossGetSession() = runTest {
        oauthLauncher.succeedWith()
        val sdk = createSdk(defaultMockEngine())

        sdk.connect(AuthProvider.Google)
        val session = sdk.getSession()

        assertNotNull(session)
        assertEquals("test-wallet-id", session.walletId)
    }

    @Test
    fun logoutClearsSession() = runTest {
        oauthLauncher.succeedWith()
        val sdk = createSdk(defaultMockEngine())

        sdk.connect(AuthProvider.Google)
        sdk.logout()

        assertNull(sdk.getSession())
    }

    @Test
    fun solanaSignMessageRequiresActiveSession() = runTest {
        val sdk = createSdk(defaultMockEngine())

        assertFailsWith<IllegalStateException> {
            sdk.solana.signMessage("Hello")
        }
    }

    @Test
    fun solanaSignMessageReturnsSignature() = runTest {
        oauthLauncher.succeedWith()
        val sdk = createSdk(defaultMockEngine())
        sdk.connect(AuthProvider.Google)

        val signature = sdk.solana.signMessage("Hello from Phantom!")
        assertEquals("sig123abc", signature)
    }

    @Test
    fun solanaSignAndSendTransactionReturnsHash() = runTest {
        oauthLauncher.succeedWith()
        val sdk = createSdk(defaultMockEngine())
        sdk.connect(AuthProvider.Google)

        val hash = sdk.solana.signAndSendTransaction("base64tx==")
        assertEquals("txhash456", hash)
    }

    @Test
    fun getSolanaAddressAfterConnect() = runTest {
        oauthLauncher.succeedWith()
        val sdk = createSdk(defaultMockEngine())
        sdk.connect(AuthProvider.Google)

        val address = sdk.getAddress(Chain.Solana)
        assertEquals("SoLaNaAddr123", address)
    }

    @Test
    fun getAddressReturnsNullWhenNotConnected() = runTest {
        val sdk = createSdk(defaultMockEngine())
        assertNull(sdk.getAddress(Chain.Solana))
    }

    @Test
    fun connectCancelledResult() = runTest {
        oauthLauncher.nextResult = OAuthResult.Cancelled("user cancelled")
        val sdk = createSdk(defaultMockEngine())

        val result = sdk.connect(AuthProvider.Google)

        assertTrue(result is ConnectResult.Cancelled)
    }

    @Test
    fun persistSessionFalseDoesNotSurviveNewInstance() = runTest {
        oauthLauncher.succeedWith()

        // Two separate in-memory session stores simulate two SDK instances (app restarts)
        val store1 = InMemorySessionStore()
        val store2 = InMemorySessionStore()
        val engine = defaultMockEngine()

        fun makeSdk(store: InMemorySessionStore): PhantomSdk {
            val httpClient = HttpClient(MockEngine { request ->
                val body = request.body.toByteArray().decodeToString()
                when {
                    body.contains("getAccounts") -> respond(
                        content = """{"result": {"accounts": [{"address": "SoLaNaAddr123"}]}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    body.contains("createOrganization") -> respond(
                        content = """{"result": {"organizationId": "org-1"}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    body.contains("createWallet") -> respond(
                        content = """{"result": {"walletId": "wallet-1"}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                    else -> respond(
                        content = """{"result": {}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            }) {
                install(ContentNegotiation) { json(json) }
            }
            return PhantomSdk.createForTesting(
                keyStore = keyStore,
                sessionStore = store,
                oauthLauncher = oauthLauncher,
                httpClient = httpClient,
                config = config,
                timeProvider = timeProvider,
            )
        }

        // First "app launch" — connect and verify session exists
        val sdk1 = makeSdk(store1)
        sdk1.connect(AuthProvider.Google)
        assertNotNull(sdk1.getSession())

        // Second "app launch" — new in-memory store, session is gone
        val sdk2 = makeSdk(store2)
        assertNull(sdk2.getSession())
    }

    @Test
    fun appWalletFlow() = runTest {
        val sdk = createSdk(defaultMockEngine())

        val result = sdk.createAppWallet()

        assertTrue(result is ConnectResult.Success)
        assertEquals("wallet-1", result.session.walletId)
        assertEquals("org-1", result.session.organizationId)
        assertEquals("device", result.session.providerId)
        assertEquals(WalletType.AppWallet, result.session.walletType)
    }
}
