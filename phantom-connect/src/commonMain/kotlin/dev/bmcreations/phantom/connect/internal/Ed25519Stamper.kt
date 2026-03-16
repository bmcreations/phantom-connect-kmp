package dev.bmcreations.phantom.connect.internal

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class PhantomStamp(
    val publicKey: String,
    val signature: String,
    val kind: String = "PKI",
    val algorithm: String = "Ed25519",
)

internal class Ed25519Stamper(
    private val keyStore: Ed25519KeyStoreProvider,
    private val activeTag: String = KeyStoreTags.ACTIVE,
) {
    private val stampJson = Json { encodeDefaults = true }
    /**
     * Construct the X-Phantom-Stamp header value for [bodyBytes].
     *
     * 1. Sign bodyBytes with the active Ed25519 private key
     * 2. Build JSON: { publicKey: base64url, signature: base64url, kind: "PKI" }
     * 3. Base64url-encode the JSON string
     */
    suspend fun stamp(bodyBytes: ByteArray): String {
        val publicKey = keyStore.getPublicKey(activeTag)
            ?: throw IllegalStateException("No active authenticator key found")
        val signature = keyStore.sign(activeTag, bodyBytes)
        val stampJsonStr = stampJson.encodeToString(
            PhantomStamp(
                publicKey = publicKey.toBase64Url(),
                signature = signature.toBase64Url(),
            )
        )
        return stampJsonStr.encodeToByteArray().toBase64Url()
    }

    /** Ensure an active keypair exists; generate one if missing. Returns 32-byte public key. */
    suspend fun ensureKeyPair(): ByteArray {
        return keyStore.getPublicKey(activeTag)
            ?: keyStore.generateKeyPair(activeTag)
    }

    /** Get the base64url-encoded public key for the active keypair. */
    suspend fun getPublicKeyBase64Url(): String {
        val publicKey = keyStore.getPublicKey(activeTag)
            ?: throw IllegalStateException("No active authenticator key found")
        return publicKey.toBase64Url()
    }

    /** Get the base58-encoded public key for the active keypair. */
    suspend fun getPublicKeyBase58(): String {
        val publicKey = keyStore.getPublicKey(activeTag)
            ?: throw IllegalStateException("No active authenticator key found")
        return publicKey.toBase58()
    }
}

/** A stamper that produces no stamp — used for unauthenticated calls like createOrganization. */
internal object NoopStamper {
    fun stamp(bodyBytes: ByteArray): String? = null
}
