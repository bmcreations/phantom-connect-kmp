package dev.bmcreations.phantom.connect.sample.utils

import dev.bmcreations.phantom.connect.sample.SOLANA_RPC_URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.collections.plus


// ── Solana RPC ──

private suspend fun solanaRpc(method: String, params: org.json.JSONArray): JSONObject =
    withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", method)
            put("params", params)
        }.toString()
        val conn = (URL(SOLANA_RPC_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            outputStream.use { it.write(body.toByteArray()) }
        }
        val response = conn.inputStream.use { it.readBytes().decodeToString() }
        conn.disconnect()
        JSONObject(response)
    }

internal suspend fun getSolBalance(address: String): Long {
    val resp = solanaRpc("getBalance", org.json.JSONArray().put(address))
    return resp.optJSONObject("result")?.optLong("value", 0L) ?: 0L
}

private suspend fun getLatestBlockhash(): String {
    val resp = solanaRpc("getLatestBlockhash", org.json.JSONArray().put(JSONObject()))
    return resp.getJSONObject("result").getJSONObject("value").getString("blockhash")
}

internal suspend fun buildSelfTransferTransaction(solanaAddress: String): String {
    val fromKey = base58Decode(solanaAddress)
    val blockhashBytes = base58Decode(getLatestBlockhash())
    val systemProgramId = ByteArray(32) // all zeros

    // Build message
    val message = ByteArray(0).let {
        var buf = byteArrayOf()
        // Header: num_required_signatures=1, num_readonly_signed=0, num_readonly_unsigned=1
        buf += byteArrayOf(1, 0, 1)
        // Account keys: 2 accounts (from/to is same address, + system program)
        buf += byteArrayOf(2) // compact-u16
        buf += fromKey
        buf += systemProgramId
        // Recent blockhash
        buf += blockhashBytes
        // Instructions: 1 instruction
        buf += byteArrayOf(1) // compact-u16
        // System Program Transfer: program_id_index=1, 2 account refs, from=0, to=0 (self-transfer)
        buf += byteArrayOf(1, 2, 0, 0)
        // Instruction data: transfer index (u32 LE) = 2, lamports (u64 LE) = 1000
        val data = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(2)
            .putLong(1000L)
            .array()
        buf += byteArrayOf(data.size.toByte())
        buf += data
        buf
    }

    // Transaction: 1 signature (placeholder) + message
    val tx = byteArrayOf(1) + ByteArray(64) + message
    return android.util.Base64.encodeToString(tx, android.util.Base64.NO_WRAP)
}

private val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

private fun base58Decode(input: String): ByteArray {
    val bytes = mutableListOf<Int>()
    for (c in input) {
        val index = BASE58_ALPHABET.indexOf(c)
        require(index >= 0) { "Invalid Base58 character: $c" }
        var carry = index
        for (j in bytes.indices.reversed()) {
            carry += bytes[j] * 58
            bytes[j] = carry and 0xFF
            carry = carry shr 8
        }
        while (carry > 0) {
            bytes.add(0, carry and 0xFF)
            carry = carry shr 8
        }
    }
    // Leading zeros
    for (c in input) {
        if (c == '1') bytes.add(0, 0) else break
    }
    return bytes.map { it.toByte() }.toByteArray()
}
