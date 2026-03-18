package dev.bmcreations.phantom.connect.internal.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
internal data class JsonRpcRequest(
    val method: String,
    val params: JsonObject,
    val timestampMs: Long,
)

@Serializable
internal data class JsonRpcResponse(
    val result: JsonElement? = null,
    val error: JsonElement? = null,
    @kotlinx.serialization.SerialName("rpc_submission_result")
    val rpcSubmissionResult: JsonElement? = null,
) {
    fun rpcError(): JsonRpcError? {
        val err = error ?: return null
        return when (err) {
            is JsonObject -> JsonRpcError(
                code = err["code"]?.jsonPrimitive?.intOrNull,
                message = err["message"]?.jsonPrimitive?.content ?: err.toString(),
                data = err["data"],
            )
            is JsonPrimitive -> JsonRpcError(
                message = err.content,
            )
            else -> JsonRpcError(message = err.toString())
        }
    }
}

@Serializable
internal data class JsonRpcError(
    val code: Int? = null,
    val message: String,
    val data: JsonElement? = null,
)

internal class PhantomApiException(
    val rpcError: JsonRpcError,
) : Exception("Phantom API error: ${rpcError.message} (code=${rpcError.code})")
