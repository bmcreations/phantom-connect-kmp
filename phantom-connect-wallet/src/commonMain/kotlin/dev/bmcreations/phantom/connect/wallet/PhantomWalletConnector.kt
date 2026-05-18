package dev.bmcreations.phantom.connect.wallet

import dev.bmcreations.phantom.connect.WalletConnector
import dev.bmcreations.phantom.connect.WalletConnectorResult
import dev.bmcreations.phantom.connect.WalletSignAllResult
import dev.bmcreations.phantom.connect.WalletSignResult

/**
 * [WalletConnector] implementation that connects via the Phantom mobile wallet app
 * using deeplinks and the Phantom deeplink protocol (X25519 encryption).
 *
 * Every signing operation deeplinks to the Phantom app — the user approves in-app
 * and the signed result comes back. Solana-only (Phantom deeplinks don't support Ethereum).
 *
 * @param deeplinkLauncher Platform-specific deeplink handler.
 * @param appUrl Your app's URL (used by Phantom to identify the dapp).
 * @param redirectUrl Full redirect URL for deeplink callbacks (e.g. "myapp://phantom-wallet-callback"
 *   for custom schemes, or "https://yourapp.com/callback" for HTTPS universal links).
 */
class PhantomWalletConnector(
    private val deeplinkLauncher: DeeplinkLauncher,
    private val appUrl: String,
    private val redirectUrl: String,
) : WalletConnector {

    override val id: String = "phantom_app"
    override val displayName: String = "Phantom Wallet"

    private val protocol = PhantomDeeplinkProtocol(appUrl, redirectUrl)

    override suspend fun isAppInstalled(): Boolean = deeplinkLauncher.isAppInstalled()

    override suspend fun connect(): WalletConnectorResult {
        val connectUrl = protocol.buildConnectUrl()

        return when (val result = deeplinkLauncher.launch(connectUrl)) {
            is DeeplinkResult.Success -> {
                try {
                    result.params["errorCode"]?.let { code ->
                        val message = result.params["errorMessage"] ?: "Unknown error"
                        return WalletConnectorResult.Error(
                            PhantomWalletException(code.toIntOrNull() ?: -1, message)
                        )
                    }

                    val publicKeyBase58 = protocol.parseConnectResponse(result.params)
                    WalletConnectorResult.Connected(publicKeyBase58)
                } catch (e: Exception) {
                    WalletConnectorResult.Error(e)
                }
            }
            is DeeplinkResult.Cancelled -> WalletConnectorResult.Cancelled(result.reason)
            is DeeplinkResult.Error -> WalletConnectorResult.Error(result.cause)
        }
    }

    override suspend fun disconnect() {
        try {
            val url = protocol.buildDisconnectUrl()
            deeplinkLauncher.launch(url)
        } catch (_: Exception) {
            // Best-effort — session state is cleared regardless.
        }
    }

    override fun exportState(): String? = protocol.exportState()

    override fun restoreState(state: String) = protocol.restoreState(state)

    override suspend fun signMessage(message: String): WalletSignResult {
        val messageBase58 = message.encodeToByteArray().toBase58()
        val url = protocol.buildSignMessageUrl(messageBase58)
        return launchAndParse(url) { params -> protocol.parseSignMessageResponse(params) }
    }

    override suspend fun signTransaction(transactionBase58: String): WalletSignResult {
        val url = protocol.buildSignTransactionUrl(transactionBase58)
        return launchAndParse(url) { params -> protocol.parseSignTransactionResponse(params) }
    }

    override suspend fun signAndSendTransaction(transactionBase58: String): WalletSignResult {
        val url = protocol.buildSignAndSendTransactionUrl(transactionBase58)
        return launchAndParse(url) { params -> protocol.parseSignAndSendTransactionResponse(params) }
    }

    override suspend fun signAllTransactions(transactionsBase58: List<String>): WalletSignAllResult {
        val url = protocol.buildSignAllTransactionsUrl(transactionsBase58)

        return when (val result = deeplinkLauncher.launch(url)) {
            is DeeplinkResult.Success -> {
                try {
                    checkForError(result.params)
                    val transactions = protocol.parseSignAllTransactionsResponse(result.params)
                    WalletSignAllResult.Success(transactions)
                } catch (e: Exception) {
                    WalletSignAllResult.Error(e)
                }
            }
            is DeeplinkResult.Cancelled -> WalletSignAllResult.Error(
                PhantomWalletException(-1, "User cancelled signing")
            )
            is DeeplinkResult.Error -> WalletSignAllResult.Error(result.cause)
        }
    }

    override fun beginCeremony() = deeplinkLauncher.beginCeremony()
    override fun endCeremony() = deeplinkLauncher.endCeremony()

    private suspend fun launchAndParse(
        url: String,
        parse: (Map<String, String>) -> String,
    ): WalletSignResult {
        return when (val result = deeplinkLauncher.launch(url)) {
            is DeeplinkResult.Success -> {
                try {
                    checkForError(result.params)
                    WalletSignResult.Success(parse(result.params))
                } catch (e: Exception) {
                    WalletSignResult.Error(e)
                }
            }
            is DeeplinkResult.Cancelled -> WalletSignResult.Error(
                PhantomWalletException(-1, "User cancelled signing")
            )
            is DeeplinkResult.Error -> WalletSignResult.Error(result.cause)
        }
    }

    private fun checkForError(params: Map<String, String>) {
        params["errorCode"]?.let { code ->
            val message = params["errorMessage"] ?: "Unknown error"
            throw PhantomWalletException(code.toIntOrNull() ?: -1, message)
        }
    }
}

class PhantomWalletException(
    val code: Int,
    override val message: String,
) : Exception("Phantom wallet error ($code): $message")
