package dev.bmcreations.phantom.connect

/**
 * Extension point for connecting via an external wallet app (e.g. Phantom mobile app).
 *
 * Unlike social-login sessions (which use a local Ed25519 keypair + KMS for signing),
 * wallet-connected sessions use the wallet app for every signing operation via deeplinks.
 *
 * Implementations live in the `phantom-connect-wallet` extension module.
 */
interface WalletConnector {
    /** Unique identifier for this connector (e.g. "phantom_app"). */
    val id: String

    /** Human-readable name shown in the connect sheet (e.g. "Phantom Wallet"). */
    val displayName: String

    /** Call-to-action label for the connect sheet button (e.g. "Continue with Phantom Wallet"). */
    val callToAction: String get() = "Continue with $displayName"

    /** Whether the wallet app is installed on this device. */
    suspend fun isAppInstalled(): Boolean = true

    /**
     * Initiate the wallet connection flow (e.g. deeplink to wallet app).
     * Returns the wallet's public key on success.
     */
    suspend fun connect(): WalletConnectorResult

    /** Disconnect from the wallet app. */
    suspend fun disconnect()

    /** Sign a UTF-8 message. Input and output are base58-encoded. */
    suspend fun signMessage(message: String): WalletSignResult

    /** Sign a transaction without broadcasting. Input is base58-encoded transaction. */
    suspend fun signTransaction(transactionBase58: String): WalletSignResult

    /** Sign and submit a transaction. Input is base58-encoded transaction. */
    suspend fun signAndSendTransaction(transactionBase58: String): WalletSignResult

    /** Batch sign multiple transactions. Inputs are base58-encoded. */
    suspend fun signAllTransactions(transactionsBase58: List<String>): WalletSignAllResult

    /**
     * Called before the connect ceremony begins.
     * Implementations can use this to keep a transparent overlay alive
     * so the host app doesn't flash between consecutive deeplink hops.
     */
    fun beginCeremony() {}

    /**
     * Called when the connect ceremony ends (success or failure).
     * Implementations should dismiss any overlay kept alive by [beginCeremony].
     */
    fun endCeremony() {}

    /**
     * Export opaque connector state for persistence across app restarts.
     * Returns null if there is no state to persist.
     */
    fun exportState(): String? = null

    /**
     * Restore connector state from a previously exported opaque string.
     */
    fun restoreState(state: String) {}
}

sealed class WalletConnectorResult {
    data class Connected(val publicKeyBase58: String) : WalletConnectorResult()
    data class Cancelled(val reason: String? = null) : WalletConnectorResult()
    data class Error(val cause: Throwable) : WalletConnectorResult()
}

sealed class WalletSignResult {
    data class Success(val signature: String) : WalletSignResult()
    data class Error(val cause: Throwable) : WalletSignResult()
}

sealed class WalletSignAllResult {
    data class Success(val signatures: List<String>) : WalletSignAllResult()
    data class Error(val cause: Throwable) : WalletSignAllResult()
}
