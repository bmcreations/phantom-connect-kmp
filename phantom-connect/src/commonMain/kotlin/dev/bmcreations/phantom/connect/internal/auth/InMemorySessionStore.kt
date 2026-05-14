package dev.bmcreations.phantom.connect.internal.auth

import dev.bmcreations.phantom.connect.PhantomSession

internal class InMemorySessionStore(
    private val shouldClearDelegate: SessionShouldClearDelegate? = null,
) : SessionStoreProvider {
    private var session: PhantomSession? = null
    private var shouldClearPreviousSession: Boolean = false

    override suspend fun save(session: PhantomSession) { this.session = session }
    override suspend fun load(): PhantomSession? = session
    override suspend fun clear() { session = null }

    override suspend fun saveShouldClearPreviousSession(shouldClear: Boolean) {
        shouldClearPreviousSession = shouldClear
        shouldClearDelegate?.save(shouldClear)
    }

    override suspend fun loadShouldClearPreviousSession(): Boolean {
        return shouldClearDelegate?.load() ?: shouldClearPreviousSession
    }
}
