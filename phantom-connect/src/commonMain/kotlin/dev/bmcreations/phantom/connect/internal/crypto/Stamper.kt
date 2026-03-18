package dev.bmcreations.phantom.connect.internal.crypto

/**
 * Abstraction over request stamping for KMS API calls.
 * The stamp is sent as the `X-Phantom-Stamp` header value.
 */
internal interface Stamper {
    suspend fun stamp(bodyBytes: ByteArray): String
}
