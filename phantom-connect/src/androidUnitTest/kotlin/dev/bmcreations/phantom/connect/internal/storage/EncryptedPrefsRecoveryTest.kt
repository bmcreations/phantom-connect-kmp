package dev.bmcreations.phantom.connect.internal.storage

import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Exercises the two-tier recovery flow in [EncryptedPrefs.createWithRecovery] without touching the
 * real AndroidKeyStore (which is unavailable on the host JVM). The framework `build` step and the
 * wipe hooks are faked so we can assert exactly when each recovery action fires.
 */
class EncryptedPrefsRecoveryTest {

    private val events = mutableListOf<String>()
    private fun wipePrefs() { events += "wipePrefs" }
    private fun wipeMasterKey() { events += "wipeMasterKey" }

    /** A [build] that throws [failures] times before returning [success]. */
    private fun flakyBuild(failures: Int, success: String = "prefs"): () -> String {
        var calls = 0
        return {
            events += "build"
            if (calls++ < failures) throw AEADBadTagException() else success
        }
    }

    @Test
    fun `opens directly when the store is healthy`() {
        val result = EncryptedPrefs.createWithRecovery(
            build = flakyBuild(failures = 0),
            wipePrefs = ::wipePrefs,
            wipeMasterKey = ::wipeMasterKey,
        )

        assertEquals("prefs", result)
        assertEquals(listOf("build"), events)
        assertTrue("wipePrefs" !in events)
        assertTrue("wipeMasterKey" !in events)
    }

    @Test
    fun `tier 1 wipes the prefs file and rebuilds`() {
        val result = EncryptedPrefs.createWithRecovery(
            build = flakyBuild(failures = 1),
            wipePrefs = ::wipePrefs,
            wipeMasterKey = ::wipeMasterKey,
        )

        assertEquals("prefs", result)
        // build → fail → wipePrefs → build → success. Master key is never touched.
        assertEquals(listOf("build", "wipePrefs", "build"), events)
        assertTrue("wipeMasterKey" !in events)
    }

    @Test
    fun `tier 2 deletes the master key then wipes and rebuilds`() {
        val result = EncryptedPrefs.createWithRecovery(
            build = flakyBuild(failures = 2),
            wipePrefs = ::wipePrefs,
            wipeMasterKey = ::wipeMasterKey,
        )

        assertEquals("prefs", result)
        assertEquals(
            listOf("build", "wipePrefs", "build", "wipeMasterKey", "wipePrefs", "build"),
            events,
        )
    }

    @Test
    fun `propagates when recovery still fails on the third attempt`() {
        assertFailsWith<AEADBadTagException> {
            EncryptedPrefs.createWithRecovery(
                build = flakyBuild(failures = 3),
                wipePrefs = ::wipePrefs,
                wipeMasterKey = ::wipeMasterKey,
            )
        }

        // Both recovery tiers were attempted before giving up.
        assertEquals(3, events.count { it == "build" })
        assertEquals(2, events.count { it == "wipePrefs" })
        assertEquals(1, events.count { it == "wipeMasterKey" })
    }
}
