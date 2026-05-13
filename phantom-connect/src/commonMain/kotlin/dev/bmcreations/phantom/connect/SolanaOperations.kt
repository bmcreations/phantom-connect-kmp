package dev.bmcreations.phantom.connect

/**
 * Chain-scoped signing operations for Solana.
 *
 * Access via [PhantomSdk.solana]:
 * ```kotlin
 * val signature = sdk.solana.signMessage("Hello")
 * val txHash = sdk.solana.signAndSendTransaction(base64Tx)
 * ```
 *
 * Equivalent to the `useSolana()` hook in the
 * [React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/sign-messages).
 */
interface SolanaOperations {
    /** Get the Solana address from the current session, or null if not connected. */
    @Throws(Exception::class)
    suspend fun getAddress(): String?

    /** Sign a UTF-8 message with the Solana key. Returns the signature. */
    @Throws(Exception::class)
    suspend fun signMessage(message: String): String

    /** Sign a transaction without broadcasting. Returns the signed transaction (base64). */
    @Throws(Exception::class)
    suspend fun signTransaction(transactionBase64: String): String

    /** Sign and submit a transaction to the network. Returns the transaction hash/signature. */
    @Throws(Exception::class)
    suspend fun signAndSendTransaction(transactionBase64: String): String

    /**
     * Sign multiple transactions in a single KMS RPC call.
     * More efficient than signing individually when multiple transactions are needed.
     * Returns a list of signed transactions (base64) in the same order as the input.
     */
    @Throws(Exception::class)
    suspend fun signAllTransactions(transactionsBase64: List<String>): List<String>

    /**
     * Sign and submit multiple transactions to the network.
     * Each transaction is signed and broadcast individually.
     * Returns a list of transaction hashes/signatures in the same order as the input.
     */
    @Throws(Exception::class)
    suspend fun signAndSendAllTransactions(transactionsBase64: List<String>): List<String>
}
