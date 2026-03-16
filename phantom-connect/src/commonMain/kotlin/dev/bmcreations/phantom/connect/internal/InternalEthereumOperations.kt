package dev.bmcreations.phantom.connect.internal

import dev.bmcreations.phantom.connect.Chain
import dev.bmcreations.phantom.connect.EthereumOperations

internal class InternalEthereumOperations(
    private val orchestrator: AuthOrchestrator,
) : EthereumOperations {

    override suspend fun getAddress(): String? =
        orchestrator.getSession()?.address(Chain.Ethereum)

    override suspend fun personalSign(message: String): String =
        orchestrator.signPersonalMessage(message)

    override suspend fun signTypedData(typedDataJson: String): String =
        orchestrator.signTypedData(typedDataJson)

    override suspend fun signTransaction(transactionBase64: String): String =
        orchestrator.signTransaction(transactionBase64, Chain.Ethereum)

    override suspend fun signAndSendTransaction(transactionBase64: String): String =
        orchestrator.signAndSendTransaction(transactionBase64, Chain.Ethereum)
}
