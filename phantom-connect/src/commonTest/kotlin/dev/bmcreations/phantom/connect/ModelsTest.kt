package dev.bmcreations.phantom.connect

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun phantomSessionRoundTrip() {
        val session = PhantomSession(
            walletId = "wallet-123",
            organizationId = "org-456",
            addresses = listOf(
                WalletAddress(chainId = "solana", address = "So1ana...", derivationPath = "m/44'/501'/0'/0'"),
            ),
            providerId = "google",
            accountDerivationIndex = 0,
            authUserId = "auth-user-789",
            sessionId = "session-abc",
            expiresAt = 1700604800000,
            authenticatorCreatedAt = 1700000000000,
            authenticatorExpiresAt = 1700604800000,
            walletType = WalletType.UserWallet,
            username = "user-def",
        )
        val encoded = json.encodeToString(session)
        val decoded = json.decodeFromString<PhantomSession>(encoded)
        assertEquals(session, decoded)
    }

    @Test
    fun phantomSessionDefaultValues() {
        val session = PhantomSession(
            walletId = "w",
            organizationId = "o",
            providerId = "device",
            accountDerivationIndex = 0,
            sessionId = "s",
            expiresAt = 0,
            authenticatorCreatedAt = 0,
            authenticatorExpiresAt = 0,
            walletType = WalletType.AppWallet,
            username = "u",
        )
        assertEquals(emptyList(), session.addresses)
        assertEquals(null, session.authUserId)
    }

    @Test
    fun walletTypeSerializesAsSnakeCase() {
        val encoded = json.encodeToString<WalletType>(WalletType.UserWallet)
        assertEquals("""{"type":"user_wallet"}""", encoded)

        val decoded = json.decodeFromString<WalletType>("""{"type":"app_wallet"}""")
        assertEquals(WalletType.AppWallet, decoded)
    }

    @Test
    fun walletAddressRoundTrip() {
        val address = WalletAddress(chainId = "ethereum", address = "0xABC...", derivationPath = "m/44'/60'/0'/0/0")
        val encoded = json.encodeToString(address)
        val decoded = json.decodeFromString<WalletAddress>(encoded)
        assertEquals(address, decoded)
    }
}
