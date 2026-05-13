package dev.bmcreations.phantom.connect.internal.crypto

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import dev.bmcreations.phantom.connect.internal.platform.CFDictionaryBuilder
import dev.bmcreations.phantom.connect.internal.platform.withCFDictionary
import platform.posix.memcpy

private const val KEYCHAIN_SERVICE = "dev.bmcreations.phantom.connect.p256keys"

/**
 * iOS P-256 key store using Security framework (SecKey) and Keychain.
 *
 * Key storage: The full X9.63 private key representation (97 bytes: 0x04||x||y||k)
 * is stored in Keychain as a generic password item. This allows reconstructing
 * the SecKey for signing operations.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosP256KeyStore : P256KeyStoreProvider {

    // Cache: tag -> (65-byte uncompressed public key, 97-byte X9.63 private key data)
    private val cache = mutableMapOf<String, Pair<ByteArray, ByteArray>>()

    override suspend fun generateKeyPair(tag: String): ByteArray = memScoped {
        val errorPtr = alloc<COpaquePointerVar>()

        // Generate P-256 key pair via Security framework
        val secKey = withCFDictionary({
            set(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
            setNumber(kSecAttrKeySizeInBits, 256)
        }) { attrs ->
            SecKeyCreateRandomKey(attrs, errorPtr.ptr.reinterpret())
        } ?: error("SecKeyCreateRandomKey failed")

        // Export as X9.63: 0x04 || x(32) || y(32) || k(32) = 97 bytes
        val exportErrorPtr = alloc<COpaquePointerVar>()
        val exportedCfData = SecKeyCopyExternalRepresentation(secKey, exportErrorPtr.ptr.reinterpret())
            ?: error("SecKeyCopyExternalRepresentation failed")

        val x963Bytes = cfDataToByteArray(exportedCfData as CFDataRef)
        CFRelease(exportedCfData)
        CFRelease(secKey)

        require(x963Bytes.size == 97 && x963Bytes[0] == 0x04.toByte()) {
            "Unexpected X9.63 key format, size=${x963Bytes.size}"
        }

        val publicKey = x963Bytes.copyOfRange(0, 65)

        // Store X9.63 data in keychain
        keychainSave(tag, x963Bytes)
        cache[tag] = publicKey to x963Bytes

        publicKey
    }

    override suspend fun sign(tag: String, data: ByteArray): ByteArray = memScoped {
        val (_, x963Bytes) = loadCachedOrFromKeychain(tag)
            ?: throw IllegalStateException("No P-256 key for tag: $tag")

        // Reconstruct SecKey from X9.63 data
        val keyData = x963Bytes.toNSData()
        val cfKeyData = CFBridgingRetain(keyData) as CFDataRef
        val errorPtr = alloc<COpaquePointerVar>()

        val secKey = withCFDictionary({
            set(kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
            set(kSecAttrKeyClass, kSecAttrKeyClassPrivate)
            setNumber(kSecAttrKeySizeInBits, 256)
        }) { attrs ->
            SecKeyCreateWithData(cfKeyData, attrs, errorPtr.ptr.reinterpret())
        } ?: error("SecKeyCreateWithData failed")

        // Sign
        val signData = data.toNSData()
        val cfSignData = CFBridgingRetain(signData) as CFDataRef
        val signErrorPtr = alloc<COpaquePointerVar>()

        val signature = SecKeyCreateSignature(
            secKey,
            kSecKeyAlgorithmECDSASignatureMessageX962SHA256,
            cfSignData,
            signErrorPtr.ptr.reinterpret(),
        ) ?: error("SecKeyCreateSignature failed")

        val sigNsData = CFBridgingRelease(signature) as NSData
        val result = sigNsData.toByteArray()

        CFRelease(cfSignData)
        CFRelease(cfKeyData)
        CFRelease(secKey)

        result
    }

    override suspend fun getRawPublicKey(tag: String): ByteArray? =
        loadCachedOrFromKeychain(tag)?.first

    override suspend fun getJwk(tag: String): P256Jwk? {
        val rawKey = getRawPublicKey(tag) ?: return null
        return rawPublicKeyToJwk(rawKey)
    }

    override suspend fun delete(tag: String) {
        cache.remove(tag)
        keychainDelete(tag)
    }

    override suspend fun exists(tag: String): Boolean {
        if (cache.containsKey(tag)) return true
        return keychainLoad(tag) != null
    }

    private fun loadCachedOrFromKeychain(tag: String): Pair<ByteArray, ByteArray>? {
        cache[tag]?.let { return it }
        val x963Bytes = keychainLoad(tag) ?: return null
        val publicKey = x963Bytes.copyOfRange(0, 65)
        cache[tag] = publicKey to x963Bytes
        return publicKey to x963Bytes
    }

    private fun keychainSave(tag: String, data: ByteArray) {
        keychainDelete(tag)
        val nsData = data.toNSData()
        withCFDictionary({
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, KEYCHAIN_SERVICE)
            set(kSecAttrAccount, tag)
            set(kSecValueData, nsData)
            set(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }) { query ->
            val status = SecItemAdd(query, null)
            check(status == errSecSuccess) { "SecItemAdd failed with status $status for tag: $tag" }
        }
    }

    private fun keychainLoad(tag: String): ByteArray? = memScoped {
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

    private fun cfDataToByteArray(cfData: CFDataRef): ByteArray {
        val length = CFDataGetLength(cfData).toInt()
        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), CFDataGetBytePtr(cfData), length.toULong())
        }
        return bytes
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
}

@OptIn(ExperimentalForeignApi::class)
internal fun CFDictionaryBuilder.setNumber(key: platform.CoreFoundation.CFTypeRef?, value: Int): CFDictionaryBuilder {
    if (key != null) {
        val nsNumber = platform.Foundation.NSNumber(int = value)
        val cfNumber = CFBridgingRetain(nsNumber) ?: return this
        return set(key, cfNumber)
    }
    return this
}
