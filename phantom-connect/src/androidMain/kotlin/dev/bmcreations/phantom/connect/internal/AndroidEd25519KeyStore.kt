package dev.bmcreations.phantom.connect.internal

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ionspin.kotlin.crypto.signature.Signature
import java.security.SecureRandom

@OptIn(ExperimentalUnsignedTypes::class)
internal class AndroidEd25519KeyStore private constructor(
    private val prefs: SharedPreferences,
) : Ed25519KeyStoreProvider {

    companion object {
        private const val PREFS_FILE = "phantom_connect_keystore"

        fun create(context: Context): AndroidEd25519KeyStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

            return AndroidEd25519KeyStore(prefs)
        }
    }

    override suspend fun generateKeyPair(tag: String): ByteArray {
        val seed = ByteArray(32)
        SecureRandom().nextBytes(seed)

        // Store seed
        prefs.edit().putString(tag, seed.toHex()).apply()

        // Derive keypair from seed
        val keyPair = Signature.seedKeypair(seed.toUByteArray())
        return keyPair.publicKey.toByteArray()
    }

    override suspend fun sign(tag: String, data: ByteArray): ByteArray {
        val seed = loadSeed(tag) ?: throw IllegalStateException("No key for tag: $tag")
        val keyPair = Signature.seedKeypair(seed.toUByteArray())
        return Signature.detached(data.toUByteArray(), keyPair.secretKey).toByteArray()
    }

    override suspend fun getPublicKey(tag: String): ByteArray? {
        val seed = loadSeed(tag) ?: return null
        val keyPair = Signature.seedKeypair(seed.toUByteArray())
        return keyPair.publicKey.toByteArray()
    }

    override suspend fun delete(tag: String) {
        prefs.edit().remove(tag).apply()
    }

    override suspend fun exists(tag: String): Boolean {
        return prefs.contains(tag)
    }

    override suspend fun move(fromTag: String, toTag: String) {
        val hex = prefs.getString(fromTag, null)
            ?: throw IllegalStateException("No key for tag: $fromTag")
        prefs.edit()
            .putString(toTag, hex)
            .remove(fromTag)
            .apply()
    }

    private fun loadSeed(tag: String): ByteArray? {
        val hex = prefs.getString(tag, null) ?: return null
        return hex.hexToByteArray()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.hexToByteArray(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
