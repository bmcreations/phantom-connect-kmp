package dev.bmcreations.phantom.connect

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import dev.bmcreations.phantom.connect.internal.auth.AuthOrchestrator
import dev.bmcreations.phantom.connect.internal.auth.InMemorySessionStore
import dev.bmcreations.phantom.connect.internal.crypto.Ed25519KeyStoreProvider
import dev.bmcreations.phantom.connect.internal.crypto.Ed25519Stamper
import dev.bmcreations.phantom.connect.internal.auth.SessionStoreProvider
import dev.bmcreations.phantom.connect.internal.network.PhantomClient
import dev.bmcreations.phantom.connect.internal.network.SolanaRpcClient
import dev.bmcreations.phantom.connect.internal.platform.*
import dev.bmcreations.phantom.connect.internal.ui.ConnectSheetProvider
import dev.bmcreations.phantom.connect.internal.ui.InternalEthereumOperations
import dev.bmcreations.phantom.connect.internal.ui.InternalSolanaOperations
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Entry point for the Phantom Connect KMP SDK.
 *
 * This is a Kotlin Multiplatform equivalent of the
 * [Phantom Connect React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/index).
 * It provides the same core capabilities — social login, session management,
 * and chain-scoped signing — for native Android and iOS apps without React Native.
 *
 * @see [Phantom SDK overview](https://docs.phantom.com/wallet-sdks-overview)
 * @see [PhantomSdkConfig] for configuration options
 */
class PhantomSdk private constructor(
    private val orchestrator: AuthOrchestrator,
    private val connectSheetProvider: ConnectSheetProvider,
    private val config: PhantomSdkConfig,
    private val connectors: List<WalletConnector> = emptyList(),
) {
    /** Theme for the connect sheet. Can be changed at runtime. */
    var theme: ConnectSheetTheme = ConnectSheetTheme.Dark

    /**
     * Chain-scoped Solana signing operations.
     *
     * Equivalent to the `useSolana()` hook in the React Native SDK.
     *
     * @see [Solana signing docs](https://docs.phantom.com/sdks/react-native-sdk/sign-messages)
     */
    val solana: SolanaOperations by lazy { InternalSolanaOperations(orchestrator) }

    /**
     * Chain-scoped Ethereum signing operations.
     *
     * Equivalent to the `useEthereum()` hook in the React Native SDK.
     */
    val ethereum: EthereumOperations by lazy { InternalEthereumOperations(orchestrator) }

    companion object {
        /**
         * Android-only: initialize the SDK with an application or activity context.
         * Must be called before [create] on Android (e.g. in Application.onCreate()).
         *
         * On iOS this is a no-op; calling it is harmless but unnecessary.
         */
        fun init(context: Any) {
            initPlatform(context)
        }

        /**
         * Create a new [PhantomSdk] instance.
         *
         * This is the KMP equivalent of wrapping your app with `PhantomProvider` in the
         * [React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/index).
         */
        fun create(
            config: PhantomSdkConfig,
            oauthLauncher: OAuthLauncher,
            connectors: List<WalletConnector> = emptyList(),
        ): PhantomSdk {
            if (!LibsodiumInitializer.isInitialized()) {
                LibsodiumInitializer.initializeWithCallback {  }
            }

            SdkLogger.logger = config.logger

            val keyStore = platformKeyStore()
            val sessionStore = if (config.persistSession) platformSessionStore() else InMemorySessionStore()
            val connectSheet = platformConnectSheetProvider()
            val timeProvider = SystemTimeProvider()
            val stamper = Ed25519Stamper(keyStore)

            val httpClient = HttpClient {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    })
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 10_000
                }
            }

            val client = PhantomClient(
                httpClient = httpClient,
                stamper = stamper,
                config = config,
                timeProvider = timeProvider,
            )

            val solanaRpcClient = SolanaRpcClient(httpClient, config.network)

            val orchestrator = AuthOrchestrator(
                client = client,
                keyStore = keyStore,
                sessionStore = sessionStore,
                oauthLauncher = oauthLauncher,
                stamper = stamper,
                config = config,
                timeProvider = timeProvider,
                connectors = connectors,
                solanaRpcClient = solanaRpcClient,
            )

            return PhantomSdk(orchestrator, connectSheet, config, connectors)
        }

        internal fun createForTesting(
            keyStore: Ed25519KeyStoreProvider,
            sessionStore: SessionStoreProvider,
            oauthLauncher: OAuthLauncher,
            httpClient: HttpClient,
            config: PhantomSdkConfig,
            timeProvider: TimeProvider = SystemTimeProvider(),
            connectSheetProvider: ConnectSheetProvider = NoopConnectSheetProvider(),
            connectors: List<WalletConnector> = emptyList(),
        ): PhantomSdk {
            SdkLogger.logger = config.logger

            val stamper = Ed25519Stamper(keyStore)
            val client = PhantomClient(
                httpClient = httpClient,
                stamper = stamper,
                config = config,
                timeProvider = timeProvider,
            )
            val solanaRpcClient = SolanaRpcClient(httpClient, config.network)
            val orchestrator = AuthOrchestrator(
                client = client,
                keyStore = keyStore,
                sessionStore = sessionStore,
                oauthLauncher = oauthLauncher,
                stamper = stamper,
                config = config,
                timeProvider = timeProvider,
                connectors = connectors,
                solanaRpcClient = solanaRpcClient,
            )
            return PhantomSdk(orchestrator, connectSheetProvider, config, connectors)
        }
    }

    // ── Connect ──

    /**
     * Show the connect sheet and let the user choose a provider.
     * The SDK handles the full flow automatically.
     *
     * Equivalent to the `ConnectButton` / `useConnect` + `useModal` pattern in the
     * [React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/connect).
     */
    @Throws(Exception::class)
    @OptIn(kotlin.experimental.ExperimentalObjCName::class)
    @kotlin.native.ObjCName("connectWithSheet")
    suspend fun connect(): ConnectResult {
        val installed = connectors.associate { it.id to it.isAppInstalled() }
        return connectSheetProvider.show(
            theme = theme,
            session = orchestrator.getSession(),
            providers = config.providers,
            connectors = connectors,
            connectorAvailability = installed,
            onConnect = { provider -> orchestrator.connectWithSocial(provider) },
            onWalletConnect = { connector -> orchestrator.connectWithExternalWallet(connector) },
            onDisconnect = { orchestrator.logout() },
        )
    }

    /**
     * Connect with a specific provider directly (bypasses the connect sheet).
     *
     * Equivalent to `connect({ provider })` from the `useConnect` hook in the
     * [React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/connect).
     */
    @Throws(Exception::class)
    suspend fun connect(provider: AuthProvider): ConnectResult =
        orchestrator.connectWithSocial(provider)

    /**
     * Connect with an external wallet connector directly (bypasses the connect sheet).
     * The wallet app is used for every subsequent signing operation via deeplinks.
     * Solana-only (Phantom deeplinks don't support Ethereum).
     */
    @Throws(Exception::class)
    @OptIn(kotlin.experimental.ExperimentalObjCName::class)
    @kotlin.native.ObjCName("connectWithWallet")
    suspend fun connect(connector: WalletConnector): ConnectResult =
        orchestrator.connectWithExternalWallet(connector)

    /** Create a programmatic app wallet (no OAuth, no browser). */
    @Throws(Exception::class)
    suspend fun createAppWallet(): ConnectResult =
        orchestrator.createAppWallet()

    // ── Session ──

    /**
     * Get the current session, auto-renewing the authenticator if needed.
     * Call on app launch to restore a previously saved session.
     *
     * Sessions are maintained for seven days with automatic authenticator renewal.
     * When [PhantomSdkConfig.persistSession] is `true` (default), sessions survive
     * app restarts via platform-secure storage (Keychain on iOS, EncryptedSharedPreferences
     * on Android). When `false`, sessions are held in memory only.
     *
     * Equivalent to checking `isConnected` / `addresses` from the `useAccounts` hook
     * in the [React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/index).
     */
    @Throws(Exception::class)
    suspend fun getSession(): PhantomSession? =
        orchestrator.getSession()

    /** Whether there is an active session. */
    @Throws(Exception::class)
    suspend fun isConnected(): Boolean =
        orchestrator.getSession() != null

    /**
     * Clear the session and keys.
     *
     * Equivalent to `disconnect()` from the `useDisconnect` hook in the
     * [React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/index).
     */
    @Throws(Exception::class)
    suspend fun logout() =
        orchestrator.logout()

    // ── Addresses ──

    /** All addresses from the current session. */
    @Throws(Exception::class)
    suspend fun getAddresses(): List<WalletAddress> =
        getSession()?.addresses.orEmpty()

    /** All addresses for a specific chain. */
    @Throws(Exception::class)
    suspend fun getAddresses(chain: Chain): List<WalletAddress> =
        getSession()?.addresses(chain).orEmpty()

    /** First address for a given chain, or null. */
    @Throws(Exception::class)
    suspend fun getAddress(chain: Chain): String? =
        getSession()?.address(chain)

}

internal class NoopConnectSheetProvider : ConnectSheetProvider {
    override suspend fun show(
        theme: ConnectSheetTheme,
        session: PhantomSession?,
        providers: List<AuthProvider>,
        connectors: List<WalletConnector>,
        connectorAvailability: Map<String, Boolean>,
        onConnect: suspend (AuthProvider) -> ConnectResult,
        onWalletConnect: suspend (WalletConnector) -> ConnectResult,
        onDisconnect: (suspend () -> Unit)?,
    ): ConnectResult = ConnectResult.Cancelled("No connect sheet available")
}
