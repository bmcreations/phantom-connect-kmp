package dev.bmcreations.phantom.connect

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import dev.bmcreations.phantom.connect.fakes.FakeEd25519KeyStore
import dev.bmcreations.phantom.connect.fakes.FakeTimeProvider
import dev.bmcreations.phantom.connect.internal.crypto.Ed25519Stamper
import dev.bmcreations.phantom.connect.internal.crypto.KeyStoreTags
import dev.bmcreations.phantom.connect.internal.network.PhantomApiException
import dev.bmcreations.phantom.connect.internal.network.PhantomClient
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalUnsignedTypes::class)
class PhantomClientTest {

    private lateinit var keyStore: FakeEd25519KeyStore
    private lateinit var timeProvider: FakeTimeProvider
    private val config = PhantomSdkConfig(
        appId = "test-app-id",
        redirectScheme = "testapp",
        redirectUri = "testapp://phantom-callback",
        baseUrl = "https://api.phantom.app",
    )
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setup() = runTest {
        LibsodiumInitializer.initialize()
        keyStore = FakeEd25519KeyStore()
        keyStore.generateKeyPair(KeyStoreTags.ACTIVE)
        timeProvider = FakeTimeProvider()
    }

    private fun createClient(mockEngine: MockEngine): PhantomClient {
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val stamper = Ed25519Stamper(keyStore)
        return PhantomClient(httpClient, stamper, config, timeProvider)
    }

    @Test
    fun callSendsCorrectJsonRpcEnvelope() = runTest {
        var capturedBody: String? = null

        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"result": {"test": true}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        client.call("testMethod", buildJsonObject { put("key", "value") })

        assertNotNull(capturedBody)
        val body = json.decodeFromString<JsonObject>(capturedBody!!)
        assertEquals("testMethod", body["method"]?.jsonPrimitive?.content)
        assertNotNull(body["params"])
        assertEquals("value", body["params"]?.jsonObject?.get("key")?.jsonPrimitive?.content)
        assertNotNull(body["timestampMs"]?.jsonPrimitive?.content)
    }

    @Test
    fun callIncludesRequiredHeaders() = runTest {
        val mockEngine = MockEngine { request ->
            // Verify headers
            assertNotNull(request.headers["X-Phantom-Stamp"], "Missing X-Phantom-Stamp header")
            assertEquals("test-app-id", request.headers["x-app-id"])
            respond(
                content = """{"result": {}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        client.call("test", buildJsonObject { })
    }

    @Test
    fun callIncludesAuthUserIdWhenProvided() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("user-123", request.headers["x-auth-user-id"])
            respond(
                content = """{"result": {}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        client.call("test", buildJsonObject { }, authUserId = "user-123")
    }

    @Test
    fun callOmitsAuthUserIdWhenNull() = runTest {
        val mockEngine = MockEngine { request ->
            assertNull(request.headers["x-auth-user-id"])
            respond(
                content = """{"result": {}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        client.call("test", buildJsonObject { })
    }

    @Test
    fun callThrowsOnRpcError() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"error": {"code": 42, "message": "something broke"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        val exception = assertFailsWith<PhantomApiException> {
            client.call("test", buildJsonObject { })
        }
        assertEquals("something broke", exception.rpcError.message)
        assertEquals(42, exception.rpcError.code)
    }

    @Test
    fun callUnwrapsResult() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"result": {"walletId": "w123"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        val result = client.call("test", buildJsonObject { })
        assertEquals("w123", result.jsonObject["walletId"]?.jsonPrimitive?.content)
    }

    @Test
    fun callUnauthenticatedOmitsStampHeader() = runTest {
        val mockEngine = MockEngine { request ->
            assertNull(request.headers["X-Phantom-Stamp"])
            assertNotNull(request.headers["x-app-id"])
            respond(
                content = """{"result": {"organizationId": "org-1"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        client.callUnauthenticated("createOrganization", buildJsonObject { })
    }

    @Test
    fun callSendsToCorrectEndpoint() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("https://api.phantom.app/v1/wallets/kms/rpc", request.url.toString())
            assertEquals(HttpMethod.Post, request.method)
            respond(
                content = """{"result": {}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        client.call("test", buildJsonObject { })
    }

    @Test
    fun createAuthenticatorSendsPublicKeyAsByteArray() = runTest {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"result": {"authenticatorId": "auth-1"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        val testKey = byteArrayOf(12, 45, -78, 100)
        client.createAuthenticator(
            organizationId = "org-1",
            username = "user-1",
            publicKeyBytes = testKey,
        )

        assertNotNull(capturedBody)
        val body = json.decodeFromString<JsonObject>(capturedBody!!)
        val params = body["params"]!!.jsonObject
        val authenticator = params["authenticator"]!!.jsonObject
        val publicKeyArray = authenticator["publicKey"]!!.jsonArray

        // Verify bytes are sent as unsigned int array (0-255)
        assertEquals(4, publicKeyArray.size)
        assertEquals(12, publicKeyArray[0].jsonPrimitive.int)
        assertEquals(45, publicKeyArray[1].jsonPrimitive.int)
        assertEquals(178, publicKeyArray[2].jsonPrimitive.int) // -78 as unsigned
        assertEquals(100, publicKeyArray[3].jsonPrimitive.int)
    }

    @Test
    fun signPersonalMessageSendsCorrectParams() = runTest {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"result": {"signature": "sig"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        client.signPersonalMessage(
            organizationId = "org-1",
            walletId = "wallet-1",
            message = "Hello",
            derivationPath = "m/44'/60'/0'/0/0",
        )

        val body = json.decodeFromString<JsonObject>(capturedBody!!)
        assertEquals("signUtf8Message", body["method"]?.jsonPrimitive?.content)
        val params = body["params"]!!.jsonObject
        assertEquals("personal_sign", params["encoding"]?.jsonPrimitive?.content)
        assertEquals("Secp256k1", params["algorithm"]?.jsonPrimitive?.content)
        val derivInfo = params["derivationInfo"]!!.jsonObject
        assertEquals("Secp256k1", derivInfo["curve"]?.jsonPrimitive?.content)
        assertEquals("Ethereum", derivInfo["addressFormat"]?.jsonPrimitive?.content)
    }

    @Test
    fun signTypedDataSendsCorrectParams() = runTest {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"result": {"signature": "sig"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        client.signTypedData(
            organizationId = "org-1",
            walletId = "wallet-1",
            typedDataJson = """{"types":{}}""",
            derivationPath = "m/44'/60'/0'/0/0",
        )

        val body = json.decodeFromString<JsonObject>(capturedBody!!)
        assertEquals("signTypedData", body["method"]?.jsonPrimitive?.content)
        val params = body["params"]!!.jsonObject
        assertEquals("""{"types":{}}""", params["typedData"]?.jsonPrimitive?.content)
        assertEquals("Secp256k1", params["algorithm"]?.jsonPrimitive?.content)
    }

    @Test
    fun signAllTransactionsSendsArray() = runTest {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"result": {"signedTransactions": ["s1", "s2"]}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        client.signAllTransactions(
            organizationId = "org-1",
            walletId = "wallet-1",
            transactionsBase64 = listOf("tx1==", "tx2=="),
            chain = Chain.Solana,
            derivationPath = "m/44'/501'/0'/0'",
        )

        val body = json.decodeFromString<JsonObject>(capturedBody!!)
        assertEquals("signAllTransactions", body["method"]?.jsonPrimitive?.content)
        val params = body["params"]!!.jsonObject
        val txArray = params["transactions"]!!.jsonArray
        assertEquals(2, txArray.size)
        assertEquals("tx1==", txArray[0].jsonPrimitive.content)
        assertEquals("tx2==", txArray[1].jsonPrimitive.content)
    }

    @Test
    fun timestampMsReflectsTimeProvider() = runTest {
        timeProvider.setToEpochMs(1234567890000)

        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"result": {}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createClient(mockEngine)
        client.call("test", buildJsonObject { })

        val body = json.decodeFromString<JsonObject>(capturedBody!!)
        assertEquals(1234567890000L, body["timestampMs"]?.jsonPrimitive?.long)
    }
}
