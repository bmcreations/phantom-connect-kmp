package dev.bmcreations.phantom.connect.fakes

import dev.bmcreations.phantom.connect.PhantomSession
import dev.bmcreations.phantom.connect.internal.SessionStoreProvider

internal class FakeSessionStore : SessionStoreProvider {
    var stored: PhantomSession? = null
        private set
    var saveCount = 0
        private set
    var clearCount = 0
        private set

    override suspend fun save(session: PhantomSession) {
        saveCount++
        stored = session
    }

    override suspend fun load(): PhantomSession? = stored

    override suspend fun clear() {
        clearCount++
        stored = null
    }
}
