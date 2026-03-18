package dev.bmcreations.phantom.connect.internal.crypto

internal interface Ed25519KeyStoreProvider {
    /** Generate a new Ed25519 keypair and store it under [tag]. Returns the 32-byte public key. */
    suspend fun generateKeyPair(tag: String): ByteArray

    /** Sign [data] with the private key stored under [tag]. Returns 64-byte Ed25519 signature. */
    suspend fun sign(tag: String, data: ByteArray): ByteArray

    /** Return the 32-byte public key for [tag], or null if none stored. */
    suspend fun getPublicKey(tag: String): ByteArray?

    /** Delete the keypair stored under [tag]. */
    suspend fun delete(tag: String)

    /** Check if a keypair exists for [tag]. */
    suspend fun exists(tag: String): Boolean

    /** Move the keypair from [fromTag] to [toTag], deleting the source. */
    suspend fun move(fromTag: String, toTag: String)
}

internal object KeyStoreTags {
    const val ACTIVE = "phantom_connect_active"
    const val PENDING = "phantom_connect_pending"
}
