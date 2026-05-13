package dev.bmcreations.phantom.connect.internal.auth

import com.ionspin.kotlin.crypto.hash.Hash
import dev.bmcreations.phantom.connect.internal.crypto.toBase64Url
import kotlin.random.Random

/**
 * PKCE (Proof Key for Code Exchange) utilities for OAuth2.
 *
 * Generates code verifier/challenge pairs per RFC 7636,
 * and derives OIDC nonces from P-256 public keys.
 */
internal object PkceFlow {

    /**
     * Generate a random code verifier: 64 random bytes → base64url → truncate to 96 chars.
     */
    fun generateCodeVerifier(): String {
        // 72 random bytes → 96 base64url chars (72 * 4/3 = 96)
        val randomBytes = Random.nextBytes(72)
        val encoded = randomBytes.toBase64Url()
        return encoded.take(96)
    }

    /**
     * Generate a code challenge from a verifier: base64url(SHA-256(utf8(verifier))).
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    fun generateCodeChallenge(verifier: String): String {
        val hash = Hash.sha256(verifier.encodeToByteArray().toUByteArray())
        return hash.toByteArray().toBase64Url()
    }

    /**
     * Derive an OIDC nonce from a raw P-256 public key and a salt string.
     * nonce = base64url(SHA-256(rawPublicKey || utf8(salt)))
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    fun deriveNonce(rawPublicKey: ByteArray, salt: String): String {
        val data = rawPublicKey + salt.encodeToByteArray()
        val hash = Hash.sha256(data.toUByteArray())
        return hash.toByteArray().toBase64Url()
    }
}
