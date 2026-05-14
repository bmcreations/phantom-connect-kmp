package dev.bmcreations.phantom.connect

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Algorithm ──

/** Cryptographic algorithm used for signing. */
enum class Algorithm(val value: String) {
    Ed25519("Ed25519"),
    Secp256k1("Secp256k1"),
    Secp256r1("secp256r1"),
}

// ── Network Identifiers ──

/** CAIP-2 network identifiers matching upstream @phantom/constants. */
object NetworkId {
    // Solana
    const val SOLANA_MAINNET = "solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"
    const val SOLANA_DEVNET = "solana:EtWTRABZaYq6iMfeYKouRu166VU2xqa1"
    const val SOLANA_TESTNET = "solana:4uhcVJyU9pJkvQyS88uRDiswHXSCkY3z"

    // Ethereum
    const val ETHEREUM_MAINNET = "eip155:1"
    const val ETHEREUM_SEPOLIA = "eip155:11155111"

    // Polygon
    const val POLYGON_MAINNET = "eip155:137"
    const val POLYGON_AMOY = "eip155:80002"

    // Base
    const val BASE_MAINNET = "eip155:8453"
    const val BASE_SEPOLIA = "eip155:84532"

    // Arbitrum
    const val ARBITRUM_MAINNET = "eip155:42161"
    const val ARBITRUM_SEPOLIA = "eip155:421614"

    // Monad
    const val MONAD_MAINNET = "eip155:143"
    const val MONAD_TESTNET = "eip155:10143"

    // Bitcoin
    const val BITCOIN_MAINNET = "bip122:000000000019d6689c085ae165831e93"
    const val BITCOIN_TESTNET = "bip122:000000000933ea01ad0ee984209779ba"

    // Sui
    const val SUI_MAINNET = "sui:mainnet"
    const val SUI_TESTNET = "sui:4c78adac"
    const val SUI_DEVNET = "sui:devnet"

    // Hypercore
    const val HYPERCORE_MAINNET = "eip155:999"
    const val HYPERCORE_TESTNET = "eip155:9999"

    /** Resolve the mainnet CAIP-2 ID for a [Network] and [Chain]. */
    fun forChainAndNetwork(chain: Chain, network: Network): String = when (chain) {
        is Chain.Solana -> when (network) {
            Network.Mainnet -> SOLANA_MAINNET
            Network.Devnet -> SOLANA_DEVNET
            Network.Testnet -> SOLANA_TESTNET
        }
        is Chain.Ethereum -> when (network) {
            Network.Mainnet -> ETHEREUM_MAINNET
            Network.Testnet, Network.Devnet -> ETHEREUM_SEPOLIA
        }
        is Chain.Polygon -> when (network) {
            Network.Mainnet -> POLYGON_MAINNET
            Network.Testnet, Network.Devnet -> POLYGON_AMOY
        }
        is Chain.Base -> when (network) {
            Network.Mainnet -> BASE_MAINNET
            Network.Testnet, Network.Devnet -> BASE_SEPOLIA
        }
        is Chain.Arbitrum -> when (network) {
            Network.Mainnet -> ARBITRUM_MAINNET
            Network.Testnet, Network.Devnet -> ARBITRUM_SEPOLIA
        }
        is Chain.Monad -> when (network) {
            Network.Mainnet -> MONAD_MAINNET
            Network.Testnet, Network.Devnet -> MONAD_TESTNET
        }
        is Chain.Bitcoin -> when (network) {
            Network.Mainnet -> BITCOIN_MAINNET
            Network.Testnet, Network.Devnet -> BITCOIN_TESTNET
        }
        is Chain.Sui -> when (network) {
            Network.Mainnet -> SUI_MAINNET
            Network.Testnet -> SUI_TESTNET
            Network.Devnet -> SUI_DEVNET
        }
        is Chain.Hypercore -> when (network) {
            Network.Mainnet -> HYPERCORE_MAINNET
            Network.Testnet, Network.Devnet -> HYPERCORE_TESTNET
        }
    }
}

// ── Chain & Address Types ──

/**
 * Blockchain supported by the SDK.
 * Each chain defines its own curve, address format, and derivation path scheme.
 */
sealed interface Chain {
    val id: String
    val curve: String
    val addressFormat: String

    /** CAIP-2 network identifier (e.g. "solana:mainnet", "eip155:1"). */
    val networkId: String

    fun derivationPath(accountIndex: Int): String

    data object Solana : Chain {
        override val id = "solana"
        override val curve = "Ed25519"
        override val addressFormat = "Solana"
        override val networkId = "solana:mainnet"
        override fun derivationPath(accountIndex: Int) = "m/44'/501'/$accountIndex'/0'"
    }

    data object Ethereum : Chain {
        override val id = "ethereum"
        override val curve = "Secp256k1"
        override val addressFormat = "Ethereum"
        override val networkId = "eip155:1"
        override fun derivationPath(accountIndex: Int) = "m/44'/60'/0'/0/$accountIndex"
    }

    data object Polygon : Chain {
        override val id = "polygon"
        override val curve = "Secp256k1"
        override val addressFormat = "Ethereum"
        override val networkId = "eip155:137"
        override fun derivationPath(accountIndex: Int) = "m/44'/60'/0'/0/$accountIndex"
    }

    data object Base : Chain {
        override val id = "base"
        override val curve = "Secp256k1"
        override val addressFormat = "Ethereum"
        override val networkId = "eip155:8453"
        override fun derivationPath(accountIndex: Int) = "m/44'/60'/0'/0/$accountIndex"
    }

    data object Arbitrum : Chain {
        override val id = "arbitrum"
        override val curve = "Secp256k1"
        override val addressFormat = "Ethereum"
        override val networkId = "eip155:42161"
        override fun derivationPath(accountIndex: Int) = "m/44'/60'/0'/0/$accountIndex"
    }

    data object Monad : Chain {
        override val id = "monad"
        override val curve = "Secp256k1"
        override val addressFormat = "Ethereum"
        override val networkId = "eip155:143"
        override fun derivationPath(accountIndex: Int) = "m/44'/60'/0'/0/$accountIndex"
    }

    data object Bitcoin : Chain {
        override val id = "bitcoin"
        override val curve = "Secp256k1"
        override val addressFormat = "Bitcoin"
        override val networkId = "bip122:000000000019d6689c085ae165831e93"
        override fun derivationPath(accountIndex: Int) = "m/84'/0'/$accountIndex'/0"
    }

    data object Sui : Chain {
        override val id = "sui"
        override val curve = "Ed25519"
        override val addressFormat = "Sui"
        override val networkId = "sui:mainnet"
        override fun derivationPath(accountIndex: Int) = "m/44'/784'/0'/0'/0'"
    }

    data object Hypercore : Chain {
        override val id = "hypercore"
        override val curve = "Secp256k1"
        override val addressFormat = "Ethereum"
        override val networkId = "eip155:999"
        override fun derivationPath(accountIndex: Int) = "m/44'/60'/0'/0/$accountIndex"
    }

    companion object {
        val all: List<Chain> = listOf(Solana, Ethereum, Polygon, Base, Arbitrum, Monad, Bitcoin, Sui, Hypercore)

        fun fromId(id: String): Chain = when (id) {
            Solana.id -> Solana
            Ethereum.id -> Ethereum
            Polygon.id -> Polygon
            Base.id -> Base
            Arbitrum.id -> Arbitrum
            Monad.id -> Monad
            Bitcoin.id -> Bitcoin
            Sui.id -> Sui
            Hypercore.id -> Hypercore
            else -> throw IllegalArgumentException("Unknown chain: $id")
        }

        /** Resolve a [Chain] from a CAIP-2 network identifier (e.g. "solana:mainnet", "eip155:1"). */
        fun fromNetworkId(networkId: String): Chain = when {
            networkId.startsWith("solana:") -> Solana
            networkId == "eip155:1" -> Ethereum
            networkId == "eip155:137" -> Polygon
            networkId == "eip155:8453" -> Base
            networkId == "eip155:42161" -> Arbitrum
            networkId == "eip155:143" -> Monad
            networkId.startsWith("eip155:") -> Ethereum // fallback for unknown EVM chains
            networkId.startsWith("bip122:") -> Bitcoin
            networkId.startsWith("sui:") -> Sui
            else -> throw IllegalArgumentException("Unknown networkId: $networkId")
        }
    }
}

// ── Auth Provider ──

/** OAuth provider for social login. */
sealed interface AuthProvider {
    val id: String
    val displayName: String

    data object Google : AuthProvider {
        override val id = "google"
        override val displayName = "Google"
    }

    data object Apple : AuthProvider {
        override val id = "apple"
        override val displayName = "Apple"
    }

    data object Phantom : AuthProvider {
        override val id = "phantom"
        override val displayName = "Phantom"
    }

    data object Device : AuthProvider {
        override val id = "device"
        override val displayName = "Device"
    }

    companion object {
        val all: List<AuthProvider> = listOf(Google, Apple)

        fun fromId(id: String): AuthProvider = when (id) {
            Google.id -> Google
            Apple.id -> Apple
            Phantom.id -> Phantom
            Device.id -> Device
            else -> throw IllegalArgumentException("Unknown provider: $id")
        }
    }
}

// ── Wallet Type ──

@Serializable
sealed interface WalletType {
    val id: String

    @Serializable
    @SerialName("user_wallet")
    data object UserWallet : WalletType {
        override val id = "user_wallet"
    }

    @Serializable
    @SerialName("app_wallet")
    data object AppWallet : WalletType {
        override val id = "app_wallet"
    }

    @Serializable
    @SerialName("deeplink_wallet")
    data object DeeplinkWallet : WalletType {
        override val id = "deeplink_wallet"
    }
}

// ── Wallet Address ──

@Serializable
data class WalletAddress(
    val chainId: String,
    val address: String,
    val derivationPath: String,
) {
    val chain: Chain get() = Chain.fromId(chainId)
}

// ── Session Status ──

@Serializable
enum class SessionStatus {
    /** Session is being established (OAuth in progress). */
    Pending,
    /** Session is fully established with wallet and organization. */
    Completed,
    /** Session establishment failed terminally. */
    Failed,
}

// ── Session ──

@Serializable
data class PhantomSession(
    val walletId: String,
    val organizationId: String,
    val addresses: List<WalletAddress> = emptyList(),
    val providerId: String,
    val accountDerivationIndex: Int,
    val authUserId: String? = null,
    val sessionId: String,
    val expiresAt: Long,
    val authenticatorCreatedAt: Long,
    val authenticatorExpiresAt: Long,
    val walletType: WalletType,
    val username: String,
    val connectorState: String? = null,
    val bearerToken: String? = null,
    val refreshToken: String? = null,
    val tokenExpiresAt: Long = 0,
    val pkceCodeVerifier: String? = null,
    val salt: String = "",
    val status: SessionStatus = SessionStatus.Completed,
) {
    /** Convenience to get the typed provider, or null for non-social sessions (e.g. app wallet). */
    val provider: AuthProvider? get() = try { AuthProvider.fromId(providerId) } catch (_: Exception) { null }

    /** All addresses for a given chain. */
    fun addresses(chain: Chain): List<WalletAddress> =
        addresses.filter { it.chainId == chain.id }

    /** First address for a given chain, or null. */
    fun address(chain: Chain): String? =
        addresses(chain).firstOrNull()?.address
}

// ── Connect Result ──

sealed class ConnectResult {
    data class Success(val session: PhantomSession) : ConnectResult()
    data class Cancelled(val reason: String? = null) : ConnectResult()
    data class Error(val cause: Throwable) : ConnectResult()
}

// ── Connect Sheet Theme ──

/**
 * Theme for the Phantom connect sheet (bottom sheet modal).
 * Use the built-in [Dark] and [Light] presets, or create a [Custom] theme.
 */
sealed class ConnectSheetTheme {
    abstract val sheetBackground: Long
    abstract val optionBackground: Long
    abstract val accentColor: Long
    abstract val textPrimary: Long
    abstract val textSecondary: Long

    /** Dark theme — default Phantom branding. */
    data object Dark : ConnectSheetTheme() {
        override val sheetBackground = 0xFF2A2A3CL
        override val optionBackground = 0xFF3A3A4EL
        override val accentColor = 0xFFAB9FF2L
        override val textPrimary = 0xFFFFFFFFL
        override val textSecondary = 0xFF9999AAL
    }

    /** Light theme. */
    data object Light : ConnectSheetTheme() {
        override val sheetBackground = 0xFFFFFFFFL
        override val optionBackground = 0xFFF5F5F5L
        override val accentColor = 0xFFAB9FF2L
        override val textPrimary = 0xFF1A1A2EL
        override val textSecondary = 0xFF6B6B80L
    }

    /** Fully custom theme. */
    data class Custom(
        override val sheetBackground: Long,
        override val optionBackground: Long,
        override val accentColor: Long,
        override val textPrimary: Long,
        override val textSecondary: Long,
    ) : ConnectSheetTheme()
}

// ── Network ──

/** Network environment. Affects which CAIP-2 networkId variant chains use. */
enum class Network { Mainnet, Devnet, Testnet }

// ── SDK Config ──

/**
 * Configuration for [PhantomSdk].
 *
 * Equivalent to the `config` prop passed to `PhantomProvider` in the
 * [React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/index).
 *
 * @property appId App ID from [Phantom Portal](https://portal.phantom.app).
 * @property redirectScheme URL scheme for OAuth callbacks (e.g. `"myapp"`). Equivalent to `scheme` in the React Native SDK.
 * @property redirectUri Full redirect URI (e.g. `"myapp://phantom-auth-callback"`). Must use the `phantom-auth-callback` host to match the upstream convention. Equivalent to `authOptions.redirectUrl` in the React Native SDK.
 * @property baseUrl KMS API base URL. Override for testing only.
 * @property authApiBaseUrl OAuth2 token exchange base URL. Override for testing only.
 * @property loginBaseUrl OAuth login base URL. Override for testing only.
 * @property providers Auth providers to offer (default: Google + Apple). Equivalent to `providers` in the React Native SDK.
 * @property chains Chains to fetch addresses for (default: Solana). Equivalent to `addressTypes` in the React Native SDK.
 * @property network Network environment (default: Mainnet).
 * @property persistSession Whether to persist sessions to platform-secure storage across app restarts.
 *   When `true` (default), sessions are stored in Keychain (iOS) or EncryptedSharedPreferences (Android).
 *   When `false`, sessions are held in memory only and lost on app restart.
 * @property logger Optional structured log handler. Equivalent to `debugConfig.enabled` in the React Native SDK.
 */
data class PhantomSdkConfig(
    val appId: String,
    val redirectScheme: String,
    val redirectUri: String,
    val baseUrl: String = "https://api.phantom.app",
    val authApiBaseUrl: String = "https://auth.phantom.app",
    val loginBaseUrl: String = "https://connect.phantom.app",
    val providers: List<AuthProvider> = AuthProvider.all,
    val chains: List<Chain> = listOf(Chain.Solana),
    val network: Network = Network.Mainnet,
    val persistSession: Boolean = true,
    val logger: PhantomLogger? = null,
    internal val sdkVersion: String = "2.0.2",
)
