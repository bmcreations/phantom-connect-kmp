package dev.bmcreations.phantom.connect

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import dev.bmcreations.phantom.connect.fakes.FakeP256KeyStore
import dev.bmcreations.phantom.connect.fakes.FakeTimeProvider
import dev.bmcreations.phantom.connect.internal.auth.JarBuilder
import dev.bmcreations.phantom.connect.internal.crypto.P256KeyStoreTags
import dev.bmcreations.phantom.connect.internal.crypto.fromBase64Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class JarBuilderTest {

    private lateinit var p256KeyStore: FakeP256KeyStore
    private lateinit var timeProvider: FakeTimeProvider
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setup() = runTest {
        LibsodiumInitializer.initialize()
        p256KeyStore = FakeP256KeyStore()
        timeProvider = FakeTimeProvider()
        p256KeyStore.generateKeyPair(P256KeyStoreTags.PENDING)
    }

    @Test
    fun buildJarProducesThreePartJwt() = runTest {
        val builder = JarBuilder(p256KeyStore, timeProvider)
        val jar = builder.buildJar(
            keyTag = P256KeyStoreTags.PENDING,
            aud = "https://auth.phantom.app",
            clientId = "test-app",
            redirectUri = "testapp://callback",
            nonce = "test-nonce",
            codeChallenge = "test-challenge",
            loginHint = "google:auth2",
            state = "session-123",
        )

        val parts = jar.split(".")
        assertEquals(3, parts.size, "JWT should have 3 parts: header.payload.signature")
    }

    @Test
    fun jarHeaderContainsEs256Algorithm() = runTest {
        val builder = JarBuilder(p256KeyStore, timeProvider)
        val jar = builder.buildJar(
            keyTag = P256KeyStoreTags.PENDING,
            aud = "https://auth.phantom.app",
            clientId = "test-app",
            redirectUri = "testapp://callback",
            nonce = "nonce",
            codeChallenge = "challenge",
            loginHint = "google:auth2",
            state = "state",
        )

        val headerJson = jar.split(".")[0].fromBase64Url().decodeToString()
        val header = json.decodeFromString(JsonObject.serializer(), headerJson)

        assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)
        assertEquals("oauth-authz-req+jwt", header["typ"]?.jsonPrimitive?.content)
        assertNotNull(header["jwk"]?.jsonObject)
        assertEquals("EC", header["jwk"]?.jsonObject?.get("kty")?.jsonPrimitive?.content)
        assertEquals("P-256", header["jwk"]?.jsonObject?.get("crv")?.jsonPrimitive?.content)
    }

    @Test
    fun jarPayloadContainsRequiredClaims() = runTest {
        val builder = JarBuilder(p256KeyStore, timeProvider)
        val jar = builder.buildJar(
            keyTag = P256KeyStoreTags.PENDING,
            aud = "https://auth.phantom.app",
            clientId = "my-app-id",
            redirectUri = "myapp://callback",
            nonce = "the-nonce",
            codeChallenge = "the-challenge",
            loginHint = "apple:auth2",
            state = "my-session-id",
        )

        val payloadJson = jar.split(".")[1].fromBase64Url().decodeToString()
        val payload = json.decodeFromString(JsonObject.serializer(), payloadJson)

        assertEquals("https://auth.phantom.app", payload["aud"]?.jsonPrimitive?.content)
        assertEquals("my-app-id", payload["client_id"]?.jsonPrimitive?.content)
        assertEquals("myapp://callback", payload["redirect_uri"]?.jsonPrimitive?.content)
        assertEquals("openid offline_access", payload["scope"]?.jsonPrimitive?.content)
        assertEquals("the-nonce", payload["nonce"]?.jsonPrimitive?.content)
        assertEquals("the-challenge", payload["code_challenge"]?.jsonPrimitive?.content)
        assertEquals("S256", payload["code_challenge_method"]?.jsonPrimitive?.content)
        assertEquals("apple:auth2", payload["login_hint"]?.jsonPrimitive?.content)
        assertEquals("my-session-id", payload["state"]?.jsonPrimitive?.content)
        assertNotNull(payload["iat"])
        assertNotNull(payload["exp"])
    }

    @Test
    fun jarExpIsFiveMinutesAfterIat() = runTest {
        timeProvider.setToEpochMs(1700000000000L) // Fixed time
        val builder = JarBuilder(p256KeyStore, timeProvider)
        val jar = builder.buildJar(
            keyTag = P256KeyStoreTags.PENDING,
            aud = "https://auth.phantom.app",
            clientId = "test",
            redirectUri = "test://cb",
            nonce = "n",
            codeChallenge = "c",
            loginHint = "google:auth2",
            state = "s",
        )

        val payloadJson = jar.split(".")[1].fromBase64Url().decodeToString()
        val payload = json.decodeFromString(JsonObject.serializer(), payloadJson)
        val iat = payload["iat"]?.jsonPrimitive?.content?.toLong()!!
        val exp = payload["exp"]?.jsonPrimitive?.content?.toLong()!!

        assertEquals(300L, exp - iat, "exp should be iat + 5 minutes (300 seconds)")
    }

    @Test
    fun jarSignatureIs64Bytes() = runTest {
        val builder = JarBuilder(p256KeyStore, timeProvider)
        val jar = builder.buildJar(
            keyTag = P256KeyStoreTags.PENDING,
            aud = "https://auth.phantom.app",
            clientId = "test",
            redirectUri = "test://cb",
            nonce = "n",
            codeChallenge = "c",
            loginHint = "google:auth2",
            state = "s",
        )

        val signatureBytes = jar.split(".")[2].fromBase64Url()
        assertEquals(64, signatureBytes.size, "Raw R||S signature should be 64 bytes")
    }

    @Test
    fun derToRawSignatureConversion() {
        // Test DER → raw R||S conversion
        val r = ByteArray(32) { (it + 1).toByte() }
        val s = ByteArray(32) { (it + 33).toByte() }
        // Build DER: 0x30 [len] 0x02 0x20 [r] 0x02 0x20 [s]
        val der = byteArrayOf(0x30, 0x44, 0x02, 0x20) + r + byteArrayOf(0x02, 0x20) + s
        val raw = JarBuilder.derToRawSignature(der)

        assertEquals(64, raw.size)
        assertContentEquals(r, raw.copyOfRange(0, 32))
        assertContentEquals(s, raw.copyOfRange(32, 64))
    }

    @Test
    fun derToRawSignatureHandlesLeadingZero() {
        // DER with leading zero in R (33 bytes for R)
        val r = byteArrayOf(0) + ByteArray(32) { (it + 1).toByte() } // 33 bytes, leading zero
        val s = ByteArray(32) { (it + 33).toByte() }
        val der = byteArrayOf(0x30, 0x45, 0x02, 0x21) + r + byteArrayOf(0x02, 0x20) + s
        val raw = JarBuilder.derToRawSignature(der)

        assertEquals(64, raw.size)
        // Leading zero should be stripped
        assertContentEquals(ByteArray(32) { (it + 1).toByte() }, raw.copyOfRange(0, 32))
    }
}
