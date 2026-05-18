package dev.bmcreations.phantom.connect.wallet

import com.ionspin.kotlin.crypto.box.Box
import com.ionspin.kotlin.crypto.box.BoxKeyPair
import com.ionspin.kotlin.crypto.box.crypto_box_NONCEBYTES
import com.ionspin.kotlin.crypto.secretbox.SecretBox
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * Implements the Phantom wallet deeplink protocol:
 * - URL construction for `phantom://v1/connect` and `phantom://v1/signMessage`
 * - X25519 DH key exchange + NaCl secretbox encrypt/decrypt
 * - Response parsing
 *
 * @see [Phantom deeplink docs](https://docs.phantom.com/solana/integrating-phantom/deeplinks-solana)
 */
internal class PhantomDeeplinkProtocol(
    private val appUrl: String,
    private val redirectUrl: String,
) {
    private var dappKeyPair: BoxKeyPair? = null
    private var sharedSecret: UByteArray? = null
    private var sessionToken: String? = null

    /** The DH public key for this session, base58-encoded. */
    val dappPublicKeyBase58: String
        get() {
            ensureKeyPair()
            return dappKeyPair!!.publicKey.toByteArray().toBase58()
        }

    private fun ensureKeyPair() {
        if (dappKeyPair == null) {
            dappKeyPair = Box.keypair()
        }
    }

    /** Export crypto state as an opaque JSON string for persistence. */
    @OptIn(ExperimentalEncodingApi::class)
    fun exportState(): String? {
        val kp = dappKeyPair ?: return null
        val secret = sharedSecret ?: return null
        val token = sessionToken ?: return null

        val state = DeeplinkProtocolState(
            dappSecretKey = kp.secretKey.toByteArray().toBase64Url(),
            dappPublicKey = kp.publicKey.toByteArray().toBase64Url(),
            sharedSecret = secret.toByteArray().toBase64Url(),
            sessionToken = token,
        )
        return Json.encodeToString(DeeplinkProtocolState.serializer(), state)
    }

    /** Restore crypto state from a previously exported JSON string. */
    @OptIn(ExperimentalEncodingApi::class, ExperimentalUnsignedTypes::class)
    fun restoreState(stateJson: String) {
        val state = Json.decodeFromString(DeeplinkProtocolState.serializer(), stateJson)
        val secretKeyBytes = state.dappSecretKey.fromBase64Url().toUByteArray()
        val publicKeyBytes = state.dappPublicKey.fromBase64Url().toUByteArray()
        dappKeyPair = BoxKeyPair(publicKeyBytes, secretKeyBytes)
        sharedSecret = state.sharedSecret.fromBase64Url().toUByteArray()
        sessionToken = state.sessionToken
    }

    private fun randomNonce(): UByteArray {
        val bytes = Random.nextBytes(crypto_box_NONCEBYTES)
        return bytes.toUByteArray()
    }

    /** Build the `phantom://v1/connect` URL. */
    fun buildConnectUrl(cluster: String = "mainnet-beta"): String {
        ensureKeyPair()
        val params = buildMap {
            put("app_url", appUrl)
            put("dapp_encryption_public_key", dappPublicKeyBase58)
            put("cluster", cluster)
            put("redirect_link", "$redirectUrl")
        }
        return "https://phantom.app/ul/v1/connect?" + params.entries.joinToString("&") { (k, v) ->
            "$k=${urlEncode(v)}"
        }
    }

    /** Build the `phantom://v1/signMessage` URL for stamping. */
    fun buildSignMessageUrl(messageBase58: String): String {
        val nonce = randomNonce()
        val payload = buildJsonObject {
            put("message", messageBase58)
            put("session", sessionToken ?: throw IllegalStateException("Not connected — call parseConnectResponse first"))
            put("display", "utf8")
        }
        val encrypted = encrypt(Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray(), nonce)

        val params = buildMap {
            put("dapp_encryption_public_key", dappPublicKeyBase58)
            put("nonce", nonce.toByteArray().toBase58())
            put("redirect_link", "$redirectUrl")
            put("payload", encrypted.toByteArray().toBase58())
        }
        return "https://phantom.app/ul/v1/signMessage?" + params.entries.joinToString("&") { (k, v) ->
            "$k=${urlEncode(v)}"
        }
    }

    /**
     * Parse the callback from `phantom://v1/connect`.
     * Returns the wallet's base58-encoded public key.
     */
    fun parseConnectResponse(params: Map<String, String>): String {
        val phantomPubKeyBase58 = params["phantom_encryption_public_key"]
            ?: throw IllegalStateException("Missing phantom_encryption_public_key in connect response")
        val nonceBase58 = params["nonce"]
            ?: throw IllegalStateException("Missing nonce in connect response")
        val dataBase58 = params["data"]
            ?: throw IllegalStateException("Missing data in connect response")

        val phantomPublicKey = phantomPubKeyBase58.fromBase58().toUByteArray()
        val nonce = nonceBase58.fromBase58().toUByteArray()
        val data = dataBase58.fromBase58().toUByteArray()

        // Derive shared secret via X25519 DH (crypto_box_beforenm)
        sharedSecret = Box.beforeNM(phantomPublicKey, dappKeyPair!!.secretKey)

        // Decrypt
        val decrypted = decrypt(data, nonce)
        val json = Json.decodeFromString(JsonObject.serializer(), decrypted.decodeToString())

        sessionToken = json["session"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing session in connect response")

        return json["public_key"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing public_key in connect response")
    }

    /**
     * Parse the callback from `phantom://v1/signMessage`.
     * Returns the base58-encoded signature.
     */
    fun parseSignMessageResponse(params: Map<String, String>): String {
        val decrypted = decryptResponse(params)
        val json = Json.decodeFromString(JsonObject.serializer(), decrypted.decodeToString())

        return json["signature"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing signature in signMessage response")
    }

    /** Build the `phantom://v1/signTransaction` URL. */
    fun buildSignTransactionUrl(transactionBase58: String): String {
        val nonce = randomNonce()
        val payload = buildJsonObject {
            put("transaction", transactionBase58)
            put("session", requireSessionToken())
        }
        val encrypted = encrypt(Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray(), nonce)
        return buildEncryptedUrl("signTransaction", nonce, encrypted)
    }

    /** Build the `phantom://v1/signAndSendTransaction` URL. */
    fun buildSignAndSendTransactionUrl(transactionBase58: String): String {
        val nonce = randomNonce()
        val payload = buildJsonObject {
            put("transaction", transactionBase58)
            put("session", requireSessionToken())
        }
        val encrypted = encrypt(Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray(), nonce)
        return buildEncryptedUrl("signAndSendTransaction", nonce, encrypted)
    }

    /** Build the `phantom://v1/signAllTransactions` URL. */
    fun buildSignAllTransactionsUrl(transactionsBase58: List<String>): String {
        val nonce = randomNonce()
        val payload = buildJsonObject {
            putJsonArray("transactions") { transactionsBase58.forEach { add(it) } }
            put("session", requireSessionToken())
        }
        val encrypted = encrypt(Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray(), nonce)
        return buildEncryptedUrl("signAllTransactions", nonce, encrypted)
    }

    /** Build the `phantom://v1/disconnect` URL. */
    fun buildDisconnectUrl(): String {
        val nonce = randomNonce()
        val payload = buildJsonObject {
            put("session", requireSessionToken())
        }
        val encrypted = encrypt(Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray(), nonce)
        return buildEncryptedUrl("disconnect", nonce, encrypted)
    }

    /**
     * Parse the callback from `phantom://v1/signTransaction`.
     * Returns the base58-encoded signed transaction.
     */
    fun parseSignTransactionResponse(params: Map<String, String>): String {
        val decrypted = decryptResponse(params)
        val json = Json.decodeFromString(JsonObject.serializer(), decrypted.decodeToString())

        return json["transaction"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing transaction in signTransaction response")
    }

    /**
     * Parse the callback from `phantom://v1/signAndSendTransaction`.
     * Returns the transaction signature.
     */
    fun parseSignAndSendTransactionResponse(params: Map<String, String>): String {
        val decrypted = decryptResponse(params)
        val json = Json.decodeFromString(JsonObject.serializer(), decrypted.decodeToString())

        return json["signature"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing signature in signAndSendTransaction response")
    }

    /**
     * Parse the callback from `phantom://v1/signAllTransactions`.
     * Returns a list of base58-encoded signed transactions.
     */
    fun parseSignAllTransactionsResponse(params: Map<String, String>): List<String> {
        val decrypted = decryptResponse(params)
        val json = Json.decodeFromString(JsonObject.serializer(), decrypted.decodeToString())

        val transactions = json["transactions"]?.jsonArray
            ?: throw IllegalStateException("Missing transactions in signAllTransactions response")
        return transactions.map { it.jsonPrimitive.content }
    }

    private fun requireSessionToken(): String =
        sessionToken ?: throw IllegalStateException("Not connected — call parseConnectResponse first")

    private fun buildEncryptedUrl(endpoint: String, nonce: UByteArray, encrypted: UByteArray): String {
        val params = buildMap {
            put("dapp_encryption_public_key", dappPublicKeyBase58)
            put("nonce", nonce.toByteArray().toBase58())
            put("redirect_link", "$redirectUrl")
            put("payload", encrypted.toByteArray().toBase58())
        }
        return "https://phantom.app/ul/v1/$endpoint?" + params.entries.joinToString("&") { (k, v) ->
            "$k=${urlEncode(v)}"
        }
    }

    private fun decryptResponse(params: Map<String, String>): ByteArray {
        val nonceBase58 = params["nonce"]
            ?: throw IllegalStateException("Missing nonce in response")
        val dataBase58 = params["data"]
            ?: throw IllegalStateException("Missing data in response")

        val nonce = nonceBase58.fromBase58().toUByteArray()
        val data = dataBase58.fromBase58().toUByteArray()
        return decrypt(data, nonce)
    }

    private fun encrypt(plaintext: ByteArray, nonce: UByteArray): UByteArray {
        val secret = sharedSecret ?: throw IllegalStateException("No shared secret")
        return SecretBox.easy(plaintext.toUByteArray(), nonce, secret)
    }

    private fun decrypt(ciphertext: UByteArray, nonce: UByteArray): ByteArray {
        val secret = sharedSecret ?: throw IllegalStateException("No shared secret")
        return SecretBox.openEasy(ciphertext, nonce, secret).toByteArray()
    }
}

@Serializable
internal data class DeeplinkProtocolState(
    val dappSecretKey: String,
    val dappPublicKey: String,
    val sharedSecret: String,
    val sessionToken: String,
)

// ── Base58 encode/decode (pure Kotlin, no java.math) ──

private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

internal fun ByteArray.toBase58(): String {
    if (isEmpty()) return ""

    var leadingZeros = 0
    for (b in this) {
        if (b.toInt() == 0) leadingZeros++ else break
    }

    val input = IntArray(size) { this[it].toInt() and 0xFF }
    val encoded = mutableListOf<Char>()

    var start = leadingZeros
    while (start < input.size) {
        var remainder = 0
        var allZero = true
        for (i in start until input.size) {
            val digit = input[i] + remainder * 256
            input[i] = digit / 58
            remainder = digit % 58
            if (input[i] != 0) allZero = false
        }
        encoded.add(BASE58_ALPHABET[remainder])
        if (allZero) break
        while (start < input.size && input[start] == 0) start++
    }

    repeat(leadingZeros) { encoded.add('1') }
    return encoded.reversed().joinToString("")
}

internal fun String.fromBase58(): ByteArray {
    if (isEmpty()) return ByteArray(0)

    var leadingOnes = 0
    for (c in this) {
        if (c == '1') leadingOnes++ else break
    }

    // Decode from base58 to base256
    val input = IntArray(length) {
        val idx = BASE58_ALPHABET.indexOf(this[it])
        if (idx < 0) throw IllegalArgumentException("Invalid Base58 character: ${this[it]}")
        idx
    }

    val decoded = mutableListOf<Int>()
    for (digit58 in input) {
        var carry = digit58
        for (i in decoded.indices.reversed()) {
            carry += decoded[i] * 58
            decoded[i] = carry % 256
            carry /= 256
        }
        while (carry > 0) {
            decoded.add(0, carry % 256)
            carry /= 256
        }
    }

    return ByteArray(leadingOnes) + decoded.map { it.toByte() }.toByteArray()
}

// ── Base64url encode/decode ──

@OptIn(ExperimentalEncodingApi::class)
internal fun ByteArray.toBase64Url(): String =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(this)

@OptIn(ExperimentalEncodingApi::class)
internal fun String.fromBase64Url(): ByteArray =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(this)

// ── URL encoding ──

private fun urlEncode(value: String): String = buildString {
    for (c in value) {
        when {
            c.isLetterOrDigit() || c in "-_.~" -> append(c)
            c == ' ' -> append("+")
            else -> {
                val bytes = c.toString().encodeToByteArray()
                for (b in bytes) {
                    append('%')
                    append((b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
    }
}
