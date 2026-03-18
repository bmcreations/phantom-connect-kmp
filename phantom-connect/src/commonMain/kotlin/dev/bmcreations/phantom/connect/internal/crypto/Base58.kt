package dev.bmcreations.phantom.connect.internal.crypto

private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

internal fun String.fromBase58(): ByteArray {
    if (isEmpty()) return ByteArray(0)

    var leadingOnes = 0
    for (c in this) {
        if (c == '1') leadingOnes++ else break
    }

    val input = IntArray(length) {
        val idx = ALPHABET.indexOf(this[it])
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

internal fun ByteArray.toBase58(): String {
    if (isEmpty()) return ""

    // Count leading zero bytes → map to leading '1's
    var leadingZeros = 0
    for (b in this) {
        if (b.toInt() == 0) leadingZeros++ else break
    }

    // Work on a mutable copy of the input as unsigned values
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
        encoded.add(ALPHABET[remainder])
        if (allZero) break
        // Skip leading zeros in the working array
        while (start < input.size && input[start] == 0) start++
    }

    // Prepend '1' for each leading zero byte
    repeat(leadingZeros) { encoded.add('1') }

    return encoded.reversed().joinToString("")
}
