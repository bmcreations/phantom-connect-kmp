package dev.bmcreations.phantom.connect

import kotlin.test.Test
import kotlin.test.assertEquals

class DerivationPathsTest {

    @Test
    fun solanaDefaultIndex() {
        assertEquals("m/44'/501'/0'/0'", Chain.Solana.derivationPath(0))
    }

    @Test
    fun solanaCustomIndex() {
        assertEquals("m/44'/501'/3'/0'", Chain.Solana.derivationPath(3))
    }

    @Test
    fun ethereumDefaultIndex() {
        assertEquals("m/44'/60'/0'/0/0", Chain.Ethereum.derivationPath(0))
    }

    @Test
    fun ethereumCustomIndex() {
        assertEquals("m/44'/60'/0'/0/5", Chain.Ethereum.derivationPath(5))
    }
}
