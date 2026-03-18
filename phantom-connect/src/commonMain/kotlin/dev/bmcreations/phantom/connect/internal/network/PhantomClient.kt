package dev.bmcreations.phantom.connect.internal.network

import dev.bmcreations.phantom.connect.Chain
import dev.bmcreations.phantom.connect.Network
import dev.bmcreations.phantom.connect.PhantomSdkConfig
import dev.bmcreations.phantom.connect.internal.crypto.Stamper
import dev.bmcreations.phantom.connect.internal.platform.SdkLogger
import dev.bmcreations.phantom.connect.internal.platform.SystemTimeProvider
import dev.bmcreations.phantom.connect.internal.platform.TimeProvider
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

private const val TAG = "PhantomClient"

private const val DEFAULT_KMS_API_VERSION = "2025-11-24";

internal class PhantomClient(
    private val httpClient: HttpClient,
    private val stamper: Stamper,
    private val config: PhantomSdkConfig,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val walletsUrl get() = "${config.baseUrl}/v1/wallets"
    private val kmsRpcUrl get() = "${config.baseUrl}/v1/wallets/kms/rpc"

    suspend fun call(
        method: String,
        params: JsonObject,
        authUserId: String? = null,
        url: String = kmsRpcUrl,
        xRpcMethod: String? = null,
    ): JsonElement {
        return callFull(method, params, authUserId, url, xRpcMethod).result
            ?: throw IllegalStateException("Phantom API returned neither result nor error")
    }

    suspend fun callFull(
        method: String,
        params: JsonObject,
        authUserId: String? = null,
        url: String = kmsRpcUrl,
        xRpcMethod: String? = null,
    ): JsonRpcResponse {
        val request = JsonRpcRequest(
            method = method,
            params = params,
            timestampMs = timeProvider.now().toEpochMilliseconds(),
        )
        val bodyString = json.encodeToString(JsonRpcRequest.serializer(), request)
        val bodyBytes = bodyString.encodeToByteArray()
        val stamp = stamper.stamp(bodyBytes)

        SdkLogger.debug(TAG, "RPC $method stamp=${stamp.take(80)}... bodyLen=${bodyBytes.size} url=$url")

        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            header("X-Phantom-Stamp", stamp)
            header("x-app-id", config.appId)
            header("x-api-version", DEFAULT_KMS_API_VERSION)
            authUserId?.let { header("x-auth-user-id", it) }
            xRpcMethod?.let { header("X-Rpc-Method", it) }
            setBody(bodyString)
        }

        val responseBody = response.bodyAsText()
        SdkLogger.debug(TAG, "RPC $method response (${response.status.value}): $responseBody")
        val rpcResponse = json.decodeFromString(JsonRpcResponse.serializer(), responseBody)

        rpcResponse.rpcError()?.let {
            SdkLogger.error(TAG, "RPC $method failed: ${it.message}")
            throw PhantomApiException(it)
        }

        SdkLogger.debug(TAG, "RPC $method succeeded (status=${response.status.value})")

        return rpcResponse
    }

    suspend fun callUnauthenticated(
        method: String,
        params: JsonObject,
    ): JsonElement {
        val request = JsonRpcRequest(
            method = method,
            params = params,
            timestampMs = timeProvider.now().toEpochMilliseconds(),
        )
        val bodyString = json.encodeToString(JsonRpcRequest.serializer(), request)

        val response = httpClient.post(walletsUrl) {
            contentType(ContentType.Application.Json)
            header("x-app-id", config.appId)
            setBody(bodyString)
        }

        val responseBody = response.bodyAsText()
        val rpcResponse = json.decodeFromString(JsonRpcResponse.serializer(), responseBody)

        rpcResponse.rpcError()?.let { throw PhantomApiException(it) }

        return rpcResponse.result
            ?: throw IllegalStateException("Phantom API returned neither result nor error")
    }

    // ── Convenience methods ──

    suspend fun createOrganization(
        organizationName: String,
        username: String,
        publicKeyBytes: ByteArray,
        expiresInMs: Long = 604800000,
    ): JsonElement {
        val authenticator = buildJsonObject {
            putJsonArray("publicKey") {
                publicKeyBytes.forEach { add(it.toInt() and 0xFF) }
            }
            put("algorithm", "Ed25519")
            put("expiresInMs", expiresInMs.toString())
        }
        val user = buildJsonObject {
            put("username", username)
            putJsonObject("policy") {
                put("type", "root")
            }
            putJsonArray("authenticators") { add(authenticator) }
        }
        val params = buildJsonObject {
            put("organizationName", organizationName)
            putJsonArray("users") { add(user) }
        }
        return callUnauthenticated("createOrganization", params)
    }


    suspend fun createWallet(
        organizationId: String,
        walletName: String,
        accounts: List<String>,
        authUserId: String? = null,
    ): JsonElement {
        val params = buildJsonObject {
            put("organizationId", organizationId)
            put("walletName", walletName)
            putJsonArray("accounts") { accounts.forEach { add(it) } }
        }
        return call("createWallet", params, authUserId)
    }

    suspend fun getAccounts(
        organizationId: String,
        walletId: String,
        accounts: List<String>,
        authUserId: String? = null,
    ): JsonElement {
        val params = buildJsonObject {
            put("organizationId", organizationId)
            put("walletId", walletId)
            putJsonArray("accounts") { accounts.forEach { add(it) } }
        }
        return call("getAccounts", params, authUserId)
    }

    suspend fun signUtf8Message(
        organizationId: String,
        walletId: String,
        message: String,
        chain: Chain,
        derivationPath: String,
        authUserId: String? = null,
    ): JsonElement {
        val params = buildJsonObject {
            put("organizationId", organizationId)
            put("walletId", walletId)
            put("message", message)
            put("algorithm", "Ed25519")
            putJsonObject("derivationInfo") {
                put("derivationPath", derivationPath)
                put("curve", chain.curve)
                put("addressFormat", chain.addressFormat)
            }
        }
        return call("signUtf8Message", params, authUserId)
    }

    suspend fun signTransaction(
        organizationId: String,
        walletId: String,
        transactionBase64: String,
        chain: Chain,
        derivationPath: String,
        authUserId: String? = null,
    ): JsonElement {
        val params = buildJsonObject {
            put("organizationId", organizationId)
            put("walletId", walletId)
            put("transaction", toBase64Url(transactionBase64))
            putJsonObject("derivationInfo") {
                put("derivationPath", derivationPath)
                put("curve", chain.curve)
                put("addressFormat", chain.addressFormat)
            }
        }
        return call(
            method = "signTransaction",
            params = params,
            authUserId = authUserId,
            xRpcMethod = "signTransaction",
        )
    }

    suspend fun signAndSubmitTransaction(
        organizationId: String,
        walletId: String,
        transactionBase64: String,
        chain: Chain,
        derivationPath: String,
        network: Network = Network.Mainnet,
        account: String? = null,
        authUserId: String? = null,
    ): JsonRpcResponse {
        val params = buildJsonObject {
            put("organizationId", organizationId)
            put("walletId", walletId)
            put("transaction", toBase64Url(transactionBase64))
            putJsonObject("derivationInfo") {
                put("derivationPath", derivationPath)
                put("curve", chain.curve)
                put("addressFormat", chain.addressFormat)
            }
            putJsonObject("submissionConfig") {
                put("chain", chain.id)
                put("network", network.name.lowercase())
            }
            if (account != null) {
                putJsonObject("simulationConfig") {
                    put("account", account)
                }
            }
        }
        return callFull(
            method = "signTransaction",
            params = params,
            authUserId = authUserId,
            xRpcMethod = "signAndSendTransaction",
        )
    }

    suspend fun signPersonalMessage(
        organizationId: String,
        walletId: String,
        message: String,
        derivationPath: String,
        authUserId: String? = null,
    ): JsonElement {
        val params = buildJsonObject {
            put("organizationId", organizationId)
            put("walletId", walletId)
            put("message", message)
            put("algorithm", "Secp256k1")
            put("encoding", "personal_sign")
            putJsonObject("derivationInfo") {
                put("derivationPath", derivationPath)
                put("curve", Chain.Ethereum.curve)
                put("addressFormat", Chain.Ethereum.addressFormat)
            }
        }
        return call("signUtf8Message", params, authUserId)
    }

    suspend fun signTypedData(
        organizationId: String,
        walletId: String,
        typedDataJson: String,
        derivationPath: String,
        authUserId: String? = null,
    ): JsonElement {
        val params = buildJsonObject {
            put("organizationId", organizationId)
            put("walletId", walletId)
            put("typedData", typedDataJson)
            put("algorithm", "Secp256k1")
            putJsonObject("derivationInfo") {
                put("derivationPath", derivationPath)
                put("curve", Chain.Ethereum.curve)
                put("addressFormat", Chain.Ethereum.addressFormat)
            }
        }
        return call("signTypedData", params, authUserId)
    }

    suspend fun signAllTransactions(
        organizationId: String,
        walletId: String,
        transactionsBase64: List<String>,
        chain: Chain,
        derivationPath: String,
        authUserId: String? = null,
    ): JsonElement {
        val params = buildJsonObject {
            put("organizationId", organizationId)
            put("walletId", walletId)
            putJsonArray("transactions") { transactionsBase64.forEach { add(it) } }
            putJsonObject("derivationInfo") {
                put("derivationPath", derivationPath)
                put("curve", chain.curve)
                put("addressFormat", chain.addressFormat)
            }
        }
        return call("signAllTransactions", params, authUserId)
    }

    suspend fun createAuthenticator(
        organizationId: String,
        username: String,
        publicKeyBytes: ByteArray,
        replaceExpirable: Boolean = true,
        authUserId: String? = null,
    ): JsonElement {
        val params = buildJsonObject {
            put("organizationId", organizationId)
            put("username", username)
            put("replaceExpirable", replaceExpirable)
            putJsonObject("authenticator") {
                putJsonArray("publicKey") {
                    publicKeyBytes.forEach { add(it.toInt() and 0xFF) }
                }
                put("algorithm", "Ed25519")
            }
        }
        return call("createAuthenticator", params, authUserId)
    }

    /**
     * Prepare a transaction for signing (spending-limits flow for user wallets).
     * Returns the (possibly augmented) transaction to send to KMS.
     */
    suspend fun prepare(
        transactionBase64: String,
        organizationId: String,
        chain: Chain,
        network: Network,
        account: String,
        authenticatorPublicKey: String?,
        xRpcMethod: String,
    ): String {
        val body = buildJsonObject {
            put("transaction", toBase64Url(transactionBase64))
            put("organizationId", organizationId)
            putJsonObject("submissionConfig") {
                put("chain", chain.id)
                put("network", network.name.lowercase())
            }
            putJsonObject("simulationConfig") {
                put("account", account)
            }
            authenticatorPublicKey?.let { put("authenticatorPublicKey", it) }
        }
        val bodyString = json.encodeToString(JsonObject.serializer(), body)

        val prepareUrl = "$walletsUrl/prepare"
        SdkLogger.debug(TAG, "prepare request: account=$account xRpcMethod=$xRpcMethod")

        val response = httpClient.post(prepareUrl) {
            contentType(ContentType.Application.Json)
            header("X-Rpc-Method", xRpcMethod)
            header("x-app-id", config.appId)
            setBody(bodyString)
        }

        val responseBody = response.bodyAsText()
        SdkLogger.debug(TAG, "prepare response (status=${response.status.value}): $responseBody")

        if (!response.status.isSuccess()) {
            throw IllegalStateException("Prepare failed (${response.status.value}): $responseBody")
        }

        val responseJson = json.decodeFromString(JsonObject.serializer(), responseBody)
        return responseJson["transaction"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing transaction in prepare response")
    }

    /** Convert standard base64 to base64url (KMS expects base64url for transactions). */
    private fun toBase64Url(base64: String): String =
        base64.replace('+', '-').replace('/', '_').trimEnd('=')
}
