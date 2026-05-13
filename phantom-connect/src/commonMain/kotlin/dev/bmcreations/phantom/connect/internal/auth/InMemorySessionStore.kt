package dev.bmcreations.phantom.connect.internal.auth

import dev.bmcreations.phantom.connect.PhantomSession

internal class InMemorySessionStore : SessionStoreProvider {
    private var session: PhantomSession? = null
    private var shouldClearPreviousSession: Boolean = false

    override suspend fun save(session: PhantomSession) { this.session = session }
    override suspend fun load(): PhantomSession? = session
    override suspend fun clear() { session = null }
    override suspend fun saveShouldClearPreviousSession(shouldClear: Boolean) { shouldClearPreviousSession = shouldClear }
    override suspend fun loadShouldClearPreviousSession(): Boolean = shouldClearPreviousSession
}
