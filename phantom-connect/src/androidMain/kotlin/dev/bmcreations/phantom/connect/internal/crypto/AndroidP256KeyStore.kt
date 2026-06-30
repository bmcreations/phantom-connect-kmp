package dev.bmcreations.phantom.connect.internal.crypto

import android.content.Context
import android.content.SharedPreferences
import dev.bmcreations.phantom.connect.internal.storage.EncryptedPrefs
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec

internal class AndroidP256KeyStore private constructor(
    private val prefs: SharedPreferences,
) : P256KeyStoreProvider {

    companion object {
        private const val PREFS_FILE = "phantom_connect_p256_keystore"

        fun create(context: Context): AndroidP256KeyStore {
            return AndroidP256KeyStore(EncryptedPrefs.create(context, PREFS_FILE))
        }
    }

    override suspend fun generateKeyPair(tag: String): ByteArray {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = kpg.generateKeyPair()

        // Store PKCS#8 encoded private key
        val privateKeyBytes = keyPair.private.encoded
        prefs.edit().putString("${tag}_private", privateKeyBytes.toHex()).apply()

        // Extract uncompressed public key (0x04 || x || y) from X.509 encoding
        val rawPublicKey = extractUncompressedPublicKey(keyPair.public.encoded)
        prefs.edit().putString("${tag}_public", rawPublicKey.toHex()).apply()

        return rawPublicKey
    }

    override suspend fun sign(tag: String, data: ByteArray): ByteArray {
        val privateKey = loadPrivateKey(tag)
            ?: throw IllegalStateException("No P-256 key for tag: $tag")
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(data)
        return signer.sign()
    }

    override suspend fun getRawPublicKey(tag: String): ByteArray? {
        val hex = safeGetString("${tag}_public") ?: return null
        return hex.hexToByteArray()
    }

    override suspend fun getJwk(tag: String): P256Jwk? {
        val rawKey = getRawPublicKey(tag) ?: return null
        return rawPublicKeyToJwk(rawKey)
    }

    override suspend fun delete(tag: String) {
        prefs.edit()
            .remove("${tag}_private")
            .remove("${tag}_public")
            .apply()
    }

    override suspend fun exists(tag: String): Boolean {
        return prefs.contains("${tag}_private")
    }

    private fun loadPrivateKey(tag: String): java.security.PrivateKey? {
        val hex = safeGetString("${tag}_private") ?: return null
        val keyBytes = hex.hexToByteArray()
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("EC").generatePrivate(keySpec)
    }

    // getString decrypts the stored value with AES-GCM; a corrupt entry must read as absent rather
    // than crash, so callers regenerate the key.
    private fun safeGetString(key: String): String? =
        try {
            prefs.getString(key, null)
        } catch (_: Exception) {
            null
        }

    /**
     * Extract the uncompressed point (65 bytes: 0x04 || x || y) from an X.509 SubjectPublicKeyInfo.
     * The EC public key in X.509 format has the uncompressed point as the last 65 bytes.
     */
    private fun extractUncompressedPublicKey(x509Encoded: ByteArray): ByteArray {
        // For P-256, the X.509 encoding is 91 bytes, with the last 65 being the uncompressed point
        val offset = x509Encoded.size - 65
        require(offset >= 0 && x509Encoded[offset] == 0x04.toByte()) {
            "Unexpected X.509 EC public key format"
        }
        return x509Encoded.copyOfRange(offset, x509Encoded.size)
    }

    private fun rawPublicKeyToJwk(rawKey: ByteArray): P256Jwk {
        require(rawKey.size == 65 && rawKey[0] == 0x04.toByte()) {
            "Expected 65-byte uncompressed P-256 public key"
        }
        val x = rawKey.copyOfRange(1, 33)
        val y = rawKey.copyOfRange(33, 65)
        return P256Jwk(
            x = x.toBase64Url(),
            y = y.toBase64Url(),
        )
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.hexToByteArray(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
