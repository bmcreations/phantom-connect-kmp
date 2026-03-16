package dev.bmcreations.phantom.connect.internal

import com.ionspin.kotlin.crypto.signature.Signature
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

private const val KEYCHAIN_SERVICE = "dev.bmcreations.phantom.connect.keys"

@OptIn(ExperimentalForeignApi::class, ExperimentalUnsignedTypes::class, BetaInteropApi::class)
internal class IosEd25519KeyStore : Ed25519KeyStoreProvider {

    private val cache = mutableMapOf<String, Pair<ByteArray, ByteArray>>()

    override suspend fun generateKeyPair(tag: String): ByteArray {
        val seed = generateRandomSeed()
        val keyPair = Signature.seedKeypair(seed.toUByteArray())
        val publicKey = keyPair.publicKey.toByteArray()

        keychainSaveSeed(tag, seed)
        cache[tag] = publicKey to seed

        return publicKey
    }

    override suspend fun sign(tag: String, data: ByteArray): ByteArray {
        val (_, seed) = loadCachedOrFromKeychain(tag)
            ?: throw IllegalStateException("No key for tag: $tag")
        val keyPair = Signature.seedKeypair(seed.toUByteArray())
        return Signature.detached(data.toUByteArray(), keyPair.secretKey).toByteArray()
    }

    override suspend fun getPublicKey(tag: String): ByteArray? =
        loadCachedOrFromKeychain(tag)?.first

    override suspend fun delete(tag: String) {
        cache.remove(tag)
        keychainDelete(tag)
    }

    override suspend fun exists(tag: String): Boolean {
        if (cache.containsKey(tag)) return true
        return keychainLoadSeed(tag) != null
    }

    override suspend fun move(fromTag: String, toTag: String) {
        val (publicKey, seed) = loadCachedOrFromKeychain(fromTag)
            ?: throw IllegalStateException("No key for tag: $fromTag")
        keychainSaveSeed(toTag, seed)
        cache[toTag] = publicKey to seed
        keychainDelete(fromTag)
        cache.remove(fromTag)
    }

    private fun loadCachedOrFromKeychain(tag: String): Pair<ByteArray, ByteArray>? {
        cache[tag]?.let { return it }
        val seed = keychainLoadSeed(tag) ?: return null
        val keyPair = Signature.seedKeypair(seed.toUByteArray())
        val publicKey = keyPair.publicKey.toByteArray()
        cache[tag] = publicKey to seed
        return publicKey to seed
    }

    private fun generateRandomSeed(): ByteArray {
        val seed = ByteArray(32)
        seed.usePinned { pinned ->
            val status = SecRandomCopyBytes(kSecRandomDefault, 32u, pinned.addressOf(0))
            check(status == errSecSuccess) { "SecRandomCopyBytes failed with status $status" }
        }
        return seed
    }

    private fun keychainSaveSeed(tag: String, seed: ByteArray) {
        keychainDelete(tag)

        val data = seed.toNSData()
        withCFDictionary({
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, KEYCHAIN_SERVICE)
            set(kSecAttrAccount, tag)
            set(kSecValueData, data)
            set(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }) { query ->
            val status = SecItemAdd(query, null)
            check(status == errSecSuccess) { "SecItemAdd failed with status $status for tag: $tag" }
        }
    }

    private fun keychainLoadSeed(tag: String): ByteArray? = memScoped {
        val result = alloc<CFTypeRefVar>()

        val status = withCFDictionary({
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, KEYCHAIN_SERVICE)
            set(kSecAttrAccount, tag)
            set(kSecMatchLimit, kSecMatchLimitOne)
            set(kSecReturnData, true)
        }) { query ->
            SecItemCopyMatching(query, result.ptr)
        }

        if (status == errSecItemNotFound) return null
        check(status == errSecSuccess) { "SecItemCopyMatching failed with status $status for tag: $tag" }

        val cfData = result.value ?: return null
        val data = CFBridgingRelease(cfData) as? NSData ?: return null
        return data.toByteArray()
    }

    private fun keychainDelete(tag: String) {
        withCFDictionary({
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, KEYCHAIN_SERVICE)
            set(kSecAttrAccount, tag)
        }) { query ->
            SecItemDelete(query)
        }
    }
}

// ── NSData <-> ByteArray helpers ────────────────────────────────────────

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return byteArrayOf()
    val bytes = ByteArray(len)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this@toByteArray.bytes, length)
    }
    return bytes
}
