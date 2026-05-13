package dev.bmcreations.phantom.connect

import dev.bmcreations.phantom.connect.internal.network.SpendingLimitError
import dev.bmcreations.phantom.connect.internal.network.TransactionBlockedError
import dev.bmcreations.phantom.connect.internal.network.parseWalletServiceError
import kotlin.test.*

class WalletServiceErrorTest {

    @Test
    fun parsesSpendingLimitError() {
        val body = """{
            "type": "spending-limit-exceeded",
            "title": "Spending limit exceeded",
            "detail": "Transaction would exceed your spending limit",
            "requestId": "req-123",
            "previousSpendCents": 5000,
            "transactionSpendCents": 2000,
            "totalSpendCents": 7000,
            "limitCents": 6000
        }"""

        val error = parseWalletServiceError(body)

        assertNotNull(error)
        assertTrue(error is SpendingLimitError)
        assertEquals("spending-limit-exceeded", error.type)
        assertEquals("Spending limit exceeded", error.title)
        assertEquals("Transaction would exceed your spending limit", error.detail)
        assertEquals("req-123", error.requestId)
        assertEquals(5000, (error as SpendingLimitError).previousSpendCents)
        assertEquals(2000, error.transactionSpendCents)
        assertEquals(7000, error.totalSpendCents)
        assertEquals(6000, error.limitCents)
    }

    @Test
    fun parsesTransactionBlockedError() {
        val body = """{
            "type": "transaction-blocked",
            "title": "Transaction blocked",
            "detail": "This transaction was flagged as suspicious",
            "requestId": "req-456",
            "scannerResult": {"risk": "high"}
        }"""

        val error = parseWalletServiceError(body)

        assertNotNull(error)
        assertTrue(error is TransactionBlockedError)
        assertEquals("transaction-blocked", error.type)
        assertEquals("This transaction was flagged as suspicious", error.detail)
        assertNotNull((error as TransactionBlockedError).scannerResult)
    }

    @Test
    fun returnsNullForUnknownErrorType() {
        val body = """{"type": "unknown-error", "title": "Unknown", "detail": "???"}"""
        assertNull(parseWalletServiceError(body))
    }

    @Test
    fun returnsNullForInvalidJson() {
        assertNull(parseWalletServiceError("not json"))
    }

    @Test
    fun returnsNullForMissingTypeField() {
        val body = """{"title": "No type", "detail": "missing type field"}"""
        assertNull(parseWalletServiceError(body))
    }

    @Test
    fun spendingLimitErrorHandlesNullOptionalFields() {
        val body = """{
            "type": "spending-limit-exceeded",
            "title": "Limit",
            "detail": "Over limit"
        }"""

        val error = parseWalletServiceError(body)
        assertNotNull(error)
        assertTrue(error is SpendingLimitError)
        assertNull(error.requestId)
        assertNull((error as SpendingLimitError).previousSpendCents)
        assertNull(error.limitCents)
    }

    @Test
    fun walletServiceErrorIsAnException() {
        val body = """{
            "type": "spending-limit-exceeded",
            "title": "Limit",
            "detail": "You exceeded your limit"
        }"""

        val error = parseWalletServiceError(body)!!
        assertTrue(error is Exception)
        assertEquals("You exceeded your limit", error.message)
    }
}
