package dev.bmcreations.phantom.connect

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import dev.bmcreations.phantom.connect.fakes.FakeEd25519KeyStore
import dev.bmcreations.phantom.connect.internal.crypto.Ed25519Stamper
import dev.bmcreations.phantom.connect.internal.crypto.KeyStoreTags
import dev.bmcreations.phantom.connect.internal.crypto.fromBase64Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class Ed25519StamperTest {

    private lateinit var keyStore: FakeEd25519KeyStore
    private lateinit var stamper: Ed25519Stamper
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setup() = runTest {
        LibsodiumInitializer.initialize()
        keyStore = FakeEd25519KeyStore()
        stamper = Ed25519Stamper(keyStore)
    }

    @Test
    fun stampThrowsWhenNoKeyExists() = runTest {
        assertFailsWith<IllegalStateException> {
            stamper.stamp("test body".encodeToByteArray())
        }
    }

    @Test
    fun stampProducesValidBase64UrlEncodedJson() = runTest {
        keyStore.generateKeyPair(KeyStoreTags.ACTIVE)

        val stamp = stamper.stamp("test body".encodeToByteArray())

        // Decode the stamp: base64url -> JSON string
        val stampJsonBytes = stamp.fromBase64Url()
        val stampJson = stampJsonBytes.decodeToString()
        val stampObj = json.decodeFromString<JsonObject>(stampJson)

        // Verify required fields
        assertNotNull(stampObj["publicKey"]?.jsonPrimitive?.content)
        assertNotNull(stampObj["signature"]?.jsonPrimitive?.content)
        assertEquals("PKI", stampObj["kind"]?.jsonPrimitive?.content)
    }

    @Test
    fun stampSignatureIsValid() = runTest {
        val publicKey = keyStore.generateKeyPair(KeyStoreTags.ACTIVE)
        val body = "test body".encodeToByteArray()

        val stamp = stamper.stamp(body)

        // Decode and verify the signature
        val stampJson = stamp.fromBase64Url().decodeToString()
        val stampObj = json.decodeFromString<JsonObject>(stampJson)
        val signatureBase64 = stampObj["signature"]!!.jsonPrimitive.content
        val signature = signatureBase64.fromBase64Url()

        // Verify using libsodium
        // verifyDetached throws if invalid, so reaching this line means success
        com.ionspin.kotlin.crypto.signature.Signature.verifyDetached(
            signature = signature.toUByteArray(),
            message = body.toUByteArray(),
            publicKey = publicKey.toUByteArray(),
        )
        // If we get here without exception, the signature is valid
    }

    @Test
    fun stampPublicKeyMatchesStoredKey() = runTest {
        val publicKey = keyStore.generateKeyPair(KeyStoreTags.ACTIVE)

        val stamp = stamper.stamp("anything".encodeToByteArray())
        val stampJson = stamp.fromBase64Url().decodeToString()
        val stampObj = json.decodeFromString<JsonObject>(stampJson)
        val stampPublicKey = stampObj["publicKey"]!!.jsonPrimitive.content.fromBase64Url()

        assertContentEquals(publicKey, stampPublicKey)
    }

    @Test
    fun ensureKeyPairGeneratesWhenMissing() = runTest {
        val key = stamper.ensureKeyPair()
        assertEquals(32, key.size)
        assertEquals(1, keyStore.generateCount)
    }

    @Test
    fun ensureKeyPairReturnsExistingWhenPresent() = runTest {
        val first = stamper.ensureKeyPair()
        val second = stamper.ensureKeyPair()
        assertContentEquals(first, second)
        assertEquals(1, keyStore.generateCount)
    }

    @Test
    fun getPublicKeyBase64UrlThrowsWhenNoKey() = runTest {
        assertFailsWith<IllegalStateException> {
            stamper.getPublicKeyBase64Url()
        }
    }

    @Test
    fun getPublicKeyBase64UrlReturnsEncodedKey() = runTest {
        keyStore.generateKeyPair(KeyStoreTags.ACTIVE)
        val encoded = stamper.getPublicKeyBase64Url()
        assertTrue(encoded.isNotEmpty())
        // Should be valid base64url (no padding, no + or /)
        assertFalse(encoded.contains("="))
        assertFalse(encoded.contains("+"))
        assertFalse(encoded.contains("/"))
    }

    @Test
    fun deterministicStampsFromSameKeyAndBody() = runTest {
        keyStore.generateKeyPair(KeyStoreTags.ACTIVE)
        val body = "deterministic test".encodeToByteArray()

        val stamp1 = stamper.stamp(body)
        val stamp2 = stamper.stamp(body)

        // Ed25519 signatures are deterministic, so same key + same message = same stamp
        assertEquals(stamp1, stamp2)
    }
}
