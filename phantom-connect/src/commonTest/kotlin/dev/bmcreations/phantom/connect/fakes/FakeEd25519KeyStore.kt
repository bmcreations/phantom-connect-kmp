package dev.bmcreations.phantom.connect.fakes

import com.ionspin.kotlin.crypto.signature.Signature
import dev.bmcreations.phantom.connect.internal.Ed25519KeyStoreProvider

@OptIn(ExperimentalUnsignedTypes::class)
internal class FakeEd25519KeyStore : Ed25519KeyStoreProvider {
    private val keys = mutableMapOf<String, Pair<ByteArray, ByteArray>>() // tag -> (publicKey, secretKey)
    var generateCount = 0
        private set

    override suspend fun generateKeyPair(tag: String): ByteArray {
        generateCount++
        // Use deterministic seed based on tag for reproducible tests
        val seed = tag.encodeToByteArray().copyOf(32)
        val keyPair = Signature.seedKeypair(seed.toUByteArray())
        keys[tag] = keyPair.publicKey.toByteArray() to keyPair.secretKey.toByteArray()
        return keyPair.publicKey.toByteArray()
    }

    override suspend fun sign(tag: String, data: ByteArray): ByteArray {
        val (_, secretKey) = keys[tag] ?: throw IllegalStateException("No key for tag: $tag")
        val signature = Signature.detached(
            message = data.toUByteArray(),
            secretKey = secretKey.toUByteArray(),
        )
        return signature.toByteArray()
    }

    override suspend fun getPublicKey(tag: String): ByteArray? =
        keys[tag]?.first

    override suspend fun delete(tag: String) {
        keys.remove(tag)
    }

    override suspend fun exists(tag: String): Boolean =
        keys.containsKey(tag)

    override suspend fun move(fromTag: String, toTag: String) {
        val keyPair = keys.remove(fromTag)
            ?: throw IllegalStateException("No key for tag: $fromTag")
        keys[toTag] = keyPair
    }
}
