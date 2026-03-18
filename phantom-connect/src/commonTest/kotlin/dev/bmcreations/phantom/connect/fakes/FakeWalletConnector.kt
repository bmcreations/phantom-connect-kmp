package dev.bmcreations.phantom.connect.fakes

import dev.bmcreations.phantom.connect.*

class FakeWalletConnector : WalletConnector {
    override val id: String = "fake_wallet"
    override val displayName: String = "Fake Wallet"

    var nextConnectResult: WalletConnectorResult = WalletConnectorResult.Cancelled("Not configured")
    var nextSignMessageResult: WalletSignResult = WalletSignResult.Error(IllegalStateException("Not configured"))
    var nextSignTransactionResult: WalletSignResult = WalletSignResult.Error(IllegalStateException("Not configured"))
    var nextSignAndSendTransactionResult: WalletSignResult = WalletSignResult.Error(IllegalStateException("Not configured"))
    var nextSignAllTransactionsResult: WalletSignAllResult = WalletSignAllResult.Error(IllegalStateException("Not configured"))

    var connectCount = 0
    var disconnectCount = 0
    var signMessageCount = 0
    var signTransactionCount = 0
    var signAndSendTransactionCount = 0
    var signAllTransactionsCount = 0
    var signedMessages = mutableListOf<String>()
    var signedTransactions = mutableListOf<String>()

    var exportedState: String? = null
    var restoredState: String? = null
    var restoreCount = 0

    override suspend fun connect(): WalletConnectorResult {
        connectCount++
        return nextConnectResult
    }

    override suspend fun disconnect() {
        disconnectCount++
    }

    override suspend fun signMessage(message: String): WalletSignResult {
        signMessageCount++
        signedMessages.add(message)
        return nextSignMessageResult
    }

    override suspend fun signTransaction(transactionBase58: String): WalletSignResult {
        signTransactionCount++
        signedTransactions.add(transactionBase58)
        return nextSignTransactionResult
    }

    override suspend fun signAndSendTransaction(transactionBase58: String): WalletSignResult {
        signAndSendTransactionCount++
        signedTransactions.add(transactionBase58)
        return nextSignAndSendTransactionResult
    }

    override suspend fun signAllTransactions(transactionsBase58: List<String>): WalletSignAllResult {
        signAllTransactionsCount++
        signedTransactions.addAll(transactionsBase58)
        return nextSignAllTransactionsResult
    }

    override fun exportState(): String? = exportedState

    override fun restoreState(state: String) {
        restoreCount++
        restoredState = state
    }

    /** Configure this connector to succeed with the given public key (base58). */
    fun succeedWith(publicKeyBase58: String = "FakeWalletPubKey123") {
        nextConnectResult = WalletConnectorResult.Connected(publicKeyBase58)
    }
}
