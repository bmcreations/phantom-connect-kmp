package dev.bmcreations.phantom.connect.internal.ui

import dev.bmcreations.phantom.connect.Chain
import dev.bmcreations.phantom.connect.SolanaOperations
import dev.bmcreations.phantom.connect.internal.auth.AuthOrchestrator

internal class InternalSolanaOperations(
    private val orchestrator: AuthOrchestrator,
) : SolanaOperations {

    override suspend fun getAddress(): String? =
        orchestrator.getSession()?.address(Chain.Solana)

    override suspend fun signMessage(message: String): String =
        orchestrator.signMessage(message, Chain.Solana)

    override suspend fun signTransaction(transactionBase64: String): String =
        orchestrator.signTransaction(transactionBase64, Chain.Solana)

    override suspend fun signAndSendTransaction(transactionBase64: String): String =
        orchestrator.signAndSendTransaction(transactionBase64, Chain.Solana)

    override suspend fun signAllTransactions(transactionsBase64: List<String>): List<String> =
        orchestrator.signAllTransactions(transactionsBase64, Chain.Solana)

    override suspend fun signAndSendAllTransactions(transactionsBase64: List<String>): List<String> =
        orchestrator.signAndSendAllTransactions(transactionsBase64, Chain.Solana)
}
