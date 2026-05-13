package dev.bmcreations.phantom.connect

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import dev.bmcreations.phantom.connect.internal.auth.PkceFlow
import dev.bmcreations.phantom.connect.internal.crypto.fromBase64Url
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PkceFlowTest {

    @BeforeTest
    fun setup() = runTest {
        LibsodiumInitializer.initialize()
    }

    @Test
    fun codeVerifierIs96Characters() {
        val verifier = PkceFlow.generateCodeVerifier()
        assertEquals(96, verifier.length)
    }

    @Test
    fun codeVerifierIsBase64UrlSafe() {
        val verifier = PkceFlow.generateCodeVerifier()
        // Base64url chars: A-Z, a-z, 0-9, -, _
        assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun codeVerifiersAreUnique() {
        val v1 = PkceFlow.generateCodeVerifier()
        val v2 = PkceFlow.generateCodeVerifier()
        assertNotEquals(v1, v2)
    }

    @Test
    fun codeChallengeIsDeterministic() {
        val verifier = "test-verifier-string-for-deterministic-check"
        val c1 = PkceFlow.generateCodeChallenge(verifier)
        val c2 = PkceFlow.generateCodeChallenge(verifier)
        assertEquals(c1, c2)
    }

    @Test
    fun codeChallengeIsBase64UrlEncoded() {
        val verifier = PkceFlow.generateCodeVerifier()
        val challenge = PkceFlow.generateCodeChallenge(verifier)
        // Should be base64url encoded SHA-256 (43 chars without padding)
        assertTrue(challenge.isNotEmpty())
        assertTrue(challenge.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun codeChallengeIs43Characters() {
        // SHA-256 = 32 bytes → base64url without padding = 43 chars
        val verifier = PkceFlow.generateCodeVerifier()
        val challenge = PkceFlow.generateCodeChallenge(verifier)
        assertEquals(43, challenge.length)
    }

    @Test
    fun differentVerifiersProduceDifferentChallenges() {
        val v1 = PkceFlow.generateCodeVerifier()
        val v2 = PkceFlow.generateCodeVerifier()
        val c1 = PkceFlow.generateCodeChallenge(v1)
        val c2 = PkceFlow.generateCodeChallenge(v2)
        assertNotEquals(c1, c2)
    }

    @Test
    fun deriveNonceIsDeterministic() {
        val publicKey = ByteArray(65) { it.toByte() }
        val salt = "test-salt"
        val n1 = PkceFlow.deriveNonce(publicKey, salt)
        val n2 = PkceFlow.deriveNonce(publicKey, salt)
        assertEquals(n1, n2)
    }

    @Test
    fun deriveNonceChangesWithDifferentPublicKey() {
        val pk1 = ByteArray(65) { it.toByte() }
        val pk2 = ByteArray(65) { (it + 1).toByte() }
        val n1 = PkceFlow.deriveNonce(pk1, "salt")
        val n2 = PkceFlow.deriveNonce(pk2, "salt")
        assertNotEquals(n1, n2)
    }

    @Test
    fun deriveNonceChangesWithDifferentSalt() {
        val pk = ByteArray(65) { it.toByte() }
        val n1 = PkceFlow.deriveNonce(pk, "salt1")
        val n2 = PkceFlow.deriveNonce(pk, "salt2")
        assertNotEquals(n1, n2)
    }

    @Test
    fun deriveNonceWithEmptySalt() {
        val pk = ByteArray(65) { it.toByte() }
        val nonce = PkceFlow.deriveNonce(pk, "")
        assertTrue(nonce.isNotEmpty())
        assertEquals(43, nonce.length) // SHA-256 base64url
    }
}
