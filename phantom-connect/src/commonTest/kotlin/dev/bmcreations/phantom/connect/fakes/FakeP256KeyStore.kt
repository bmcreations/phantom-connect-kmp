package dev.bmcreations.phantom.connect.fakes

import dev.bmcreations.phantom.connect.internal.crypto.P256Jwk
import dev.bmcreations.phantom.connect.internal.crypto.P256KeyStoreProvider
import dev.bmcreations.phantom.connect.internal.crypto.toBase64Url
import kotlin.random.Random

/**
 * Test fake for P-256 key store. Uses deterministic keys based on tag.
 */
internal class FakeP256KeyStore : P256KeyStoreProvider {
    private val keys = mutableMapOf<String, ByteArray>() // tag -> 65-byte "public key"
    var generateCount = 0
        private set
    var signCount = 0
        private set

    override suspend fun generateKeyPair(tag: String): ByteArray {
        generateCount++
        // Generate deterministic 65-byte "public key" from tag
        val seed = tag.encodeToByteArray().copyOf(32)
        val publicKey = ByteArray(65)
        publicKey[0] = 0x04
        seed.copyInto(publicKey, 1, 0, 32)
        seed.reversed().toByteArray().copyInto(publicKey, 33, 0, 32)
        keys[tag] = publicKey
        return publicKey
    }

    override suspend fun sign(tag: String, data: ByteArray): ByteArray {
        signCount++
        if (!keys.containsKey(tag)) throw IllegalStateException("No P-256 key for tag: $tag")
        // Return a fake DER-encoded ECDSA signature
        // DER: 0x30 [len] 0x02 [rlen] [r...] 0x02 [slen] [s...]
        val r = ByteArray(32) { (it + data.size).toByte() }
        val s = ByteArray(32) { (it + data.size + 32).toByte() }
        return byteArrayOf(0x30, 0x44, 0x02, 0x20) + r + byteArrayOf(0x02, 0x20) + s
    }

    override suspend fun getRawPublicKey(tag: String): ByteArray? = keys[tag]

    override suspend fun getJwk(tag: String): P256Jwk? {
        val rawKey = keys[tag] ?: return null
        val x = rawKey.copyOfRange(1, 33)
        val y = rawKey.copyOfRange(33, 65)
        return P256Jwk(
            x = x.toBase64Url(),
            y = y.toBase64Url(),
        )
    }

    override suspend fun delete(tag: String) {
        keys.remove(tag)
    }

    override suspend fun exists(tag: String): Boolean = keys.containsKey(tag)
}
