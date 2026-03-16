package dev.bmcreations.phantom.connect

/**
 * Chain-scoped signing operations for Ethereum.
 *
 * Access via [PhantomSdk.ethereum]:
 * ```kotlin
 * val sig = sdk.ethereum.personalSign("Hello")
 * val typedSig = sdk.ethereum.signTypedData(eip712Json)
 * ```
 *
 * Equivalent to the `useEthereum()` hook in the
 * [React Native SDK](https://docs.phantom.com/sdks/react-native-sdk/index).
 */
interface EthereumOperations {
    /** Get the Ethereum address from the current session, or null if not connected. */
    @Throws(Exception::class)
    suspend fun getAddress(): String?

    /**
     * EIP-191 personal_sign.
     * Signs a human-readable message with the `\x19Ethereum Signed Message:\n` prefix.
     * Returns the signature.
     */
    @Throws(Exception::class)
    suspend fun personalSign(message: String): String

    /**
     * EIP-712 signTypedData_v4.
     * Signs structured typed data. Pass the full EIP-712 JSON payload as a string
     * (including `types`, `primaryType`, `domain`, and `message` fields).
     * Returns the signature.
     */
    @Throws(Exception::class)
    suspend fun signTypedData(typedDataJson: String): String

    /** Sign a transaction without broadcasting. Returns the signed transaction (base64). */
    @Throws(Exception::class)
    suspend fun signTransaction(transactionBase64: String): String

    /** Sign and submit a transaction to the network. Returns the transaction hash. */
    @Throws(Exception::class)
    suspend fun signAndSendTransaction(transactionBase64: String): String
}
