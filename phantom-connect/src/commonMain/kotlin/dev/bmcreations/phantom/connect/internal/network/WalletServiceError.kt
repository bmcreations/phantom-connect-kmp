package dev.bmcreations.phantom.connect.internal.network

import kotlinx.serialization.json.*

/**
 * Structured error types from the Phantom wallet service.
 * These are returned from the /prepare endpoint for spending limits
 * and transaction blocking.
 */
sealed class WalletServiceError(message: String) : Exception(message) {
    abstract val type: String
    abstract val title: String
    abstract val detail: String
    abstract val requestId: String?
}

class SpendingLimitError(
    override val type: String,
    override val title: String,
    override val detail: String,
    override val requestId: String?,
    val previousSpendCents: Int?,
    val transactionSpendCents: Int?,
    val totalSpendCents: Int?,
    val limitCents: Int?,
) : WalletServiceError(detail)

class TransactionBlockedError(
    override val type: String,
    override val title: String,
    override val detail: String,
    override val requestId: String?,
    val scannerResult: JsonElement?,
) : WalletServiceError(detail)

/** Check if an HTTP status code is an authentication error (401/403). */
internal fun isAuthenticationError(statusCode: Int): Boolean =
    statusCode == 401 || statusCode == 403

/** Extract a human-readable error message from any throwable. */
internal fun getErrorMessage(error: Throwable): String = when (error) {
    is WalletServiceError -> error.detail.ifEmpty { error.title }
    is PhantomApiException -> error.rpcError.message
    else -> error.message ?: error.toString()
}

/**
 * Parse an HTTP error response body into a [WalletServiceError] subtype.
 * Returns null if the response doesn't match a known error format.
 */
internal fun parseWalletServiceError(responseBody: String): WalletServiceError? {
    val json = Json { ignoreUnknownKeys = true }
    val obj = try {
        json.decodeFromString(JsonObject.serializer(), responseBody)
    } catch (_: Exception) {
        return null
    }

    val type = obj["type"]?.jsonPrimitive?.content ?: return null
    val title = obj["title"]?.jsonPrimitive?.content ?: ""
    val detail = obj["detail"]?.jsonPrimitive?.content ?: ""
    val requestId = obj["requestId"]?.jsonPrimitive?.content

    return when (type) {
        "spending-limit-exceeded" -> SpendingLimitError(
            type = type,
            title = title,
            detail = detail,
            requestId = requestId,
            previousSpendCents = obj["previousSpendCents"]?.jsonPrimitive?.intOrNull,
            transactionSpendCents = obj["transactionSpendCents"]?.jsonPrimitive?.intOrNull,
            totalSpendCents = obj["totalSpendCents"]?.jsonPrimitive?.intOrNull,
            limitCents = obj["limitCents"]?.jsonPrimitive?.intOrNull,
        )
        "transaction-blocked" -> TransactionBlockedError(
            type = type,
            title = title,
            detail = detail,
            requestId = requestId,
            scannerResult = obj["scannerResult"],
        )
        else -> null
    }
}
