package dev.bmcreations.phantom.connect.internal.auth

import dev.bmcreations.phantom.connect.PhantomSession

internal class InMemorySessionStore : SessionStoreProvider {
    private var session: PhantomSession? = null

    override suspend fun save(session: PhantomSession) { this.session = session }
    override suspend fun load(): PhantomSession? = session
    override suspend fun clear() { session = null }
}
