package dev.bmcreations.phantom.connect.internal.crypto

import kotlinx.serialization.Serializable

/**
 * P-256 (secp256r1) key management for OIDC authentication.
 * Used for ES256 JWT signing (JAR) and OIDC stamp construction.
 *
 * Platform implementations use native crypto:
 * - Android: java.security.KeyPairGenerator with ECGenParameterSpec("secp256r1")
 * - iOS: SecKeyCreateRandomKey with kSecAttrKeyTypeECSECPrimeRandom
 */
internal interface P256KeyStoreProvider {
    /** Generate a new P-256 keypair and store it under [tag]. Returns 65-byte uncompressed public key (0x04 || x || y). */
    suspend fun generateKeyPair(tag: String): ByteArray

    /** Sign [data] with the private key stored under [tag]. Returns DER-encoded ECDSA-SHA256 signature. */
    suspend fun sign(tag: String, data: ByteArray): ByteArray

    /** Return the 65-byte uncompressed public key for [tag], or null if none stored. */
    suspend fun getRawPublicKey(tag: String): ByteArray?

    /** Return the JWK representation of the public key for [tag], or null if none stored. */
    suspend fun getJwk(tag: String): P256Jwk?

    /** Delete the keypair stored under [tag]. */
    suspend fun delete(tag: String)

    /** Check if a keypair exists for [tag]. */
    suspend fun exists(tag: String): Boolean
}

@Serializable
internal data class P256Jwk(
    val kty: String = "EC",
    val crv: String = "P-256",
    val x: String,
    val y: String,
)

internal object P256KeyStoreTags {
    const val ACTIVE = "phantom_connect_p256_active"
    const val PENDING = "phantom_connect_p256_pending"
}
