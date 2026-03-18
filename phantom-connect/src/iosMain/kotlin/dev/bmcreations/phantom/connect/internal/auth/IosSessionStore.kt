package dev.bmcreations.phantom.connect.internal.auth

import dev.bmcreations.phantom.connect.PhantomSession
import dev.bmcreations.phantom.connect.internal.crypto.toByteArray
import dev.bmcreations.phantom.connect.internal.crypto.toNSData
import dev.bmcreations.phantom.connect.internal.platform.withCFDictionary
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
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
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val KEYCHAIN_SERVICE = "dev.bmcreations.phantom.connect.session"
private const val KEYCHAIN_ACCOUNT = "phantom_session"

@OptIn(ExperimentalForeignApi::class)
internal class IosSessionStore : SessionStoreProvider {

    private val json = Json { ignoreUnknownKeys = true }

    private var cached: PhantomSession? = null
    private var cacheLoaded = false

    override suspend fun save(session: PhantomSession) {
        val jsonString = json.encodeToString(session)
        val data = jsonString.encodeToByteArray().toNSData()

        keychainDelete()

        withCFDictionary({
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, KEYCHAIN_SERVICE)
            set(kSecAttrAccount, KEYCHAIN_ACCOUNT)
            set(kSecValueData, data)
            set(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }) { query ->
            val status = SecItemAdd(query, null)
            check(status == errSecSuccess) { "SecItemAdd failed with status $status" }
        }

        cached = session
        cacheLoaded = true
    }

    override suspend fun load(): PhantomSession? {
        if (cacheLoaded) return cached

        val data = keychainLoad()
        cacheLoaded = true
        if (data == null) {
            cached = null
            return null
        }

        val jsonString = data.toByteArray().decodeToString()
        cached = json.decodeFromString<PhantomSession>(jsonString)
        return cached
    }

    override suspend fun clear() {
        keychainDelete()
        cached = null
        cacheLoaded = true
    }

    private fun keychainLoad(): NSData? = memScoped {
        val result = alloc<CFTypeRefVar>()

        val status = withCFDictionary({
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, KEYCHAIN_SERVICE)
            set(kSecAttrAccount, KEYCHAIN_ACCOUNT)
            set(kSecMatchLimit, kSecMatchLimitOne)
            set(kSecReturnData, true)
        }) { query ->
            SecItemCopyMatching(query, result.ptr)
        }

        if (status == errSecItemNotFound) return null
        check(status == errSecSuccess) { "SecItemCopyMatching failed with status $status" }

        val cfData = result.value ?: return null
        return CFBridgingRelease(cfData) as? NSData
    }

    private fun keychainDelete() {
        withCFDictionary({
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, KEYCHAIN_SERVICE)
            set(kSecAttrAccount, KEYCHAIN_ACCOUNT)
        }) { query ->
            SecItemDelete(query)
        }
    }
}
