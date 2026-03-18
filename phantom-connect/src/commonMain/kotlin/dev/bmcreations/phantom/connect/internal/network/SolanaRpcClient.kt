package dev.bmcreations.phantom.connect.internal.network

import dev.bmcreations.phantom.connect.Network
import dev.bmcreations.phantom.connect.internal.platform.SdkLogger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

private const val TAG = "SolanaRpcClient"

/**
 * Minimal Solana JSON-RPC client for submitting signed transactions.
 *
 * Used by [AuthOrchestrator] when deeplink sessions need sign-then-submit
 * (Phantom deprecated the `signAndSendTransaction` deeplink).
 */
internal class SolanaRpcClient(
    private val httpClient: HttpClient,
    private val network: Network,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val rpcUrl: String get() = when (network) {
        Network.Mainnet -> "https://api.mainnet-beta.solana.com"
        Network.Devnet -> "https://api.devnet.solana.com"
        Network.Testnet -> "https://api.testnet.solana.com"
    }

    /**
     * Submit a signed transaction to the Solana network.
     *
     * @param signedTxBase64 The fully-signed transaction, base64-encoded.
     * @return The transaction signature (base58).
     */
    suspend fun sendTransaction(signedTxBase64: String): String {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "sendTransaction")
            putJsonArray("params") {
                add(signedTxBase64)
                add(buildJsonObject {
                    put("encoding", "base64")
                })
            }
        }

        val bodyString = json.encodeToString(JsonObject.serializer(), body)
        SdkLogger.debug(TAG, "sendTransaction to $rpcUrl")

        val response = httpClient.post(rpcUrl) {
            contentType(ContentType.Application.Json)
            setBody(bodyString)
        }

        val responseBody = response.bodyAsText()
        SdkLogger.debug(TAG, "sendTransaction response (${response.status.value}): $responseBody")

        val rpcResponse = json.decodeFromString(JsonObject.serializer(), responseBody)

        val error = rpcResponse["error"]
        if (error is JsonObject) {
            val message = error["message"]?.jsonPrimitive?.content ?: error.toString()
            throw IllegalStateException("Solana RPC sendTransaction failed: $message")
        }

        return rpcResponse["result"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing result in sendTransaction response")
    }
}
