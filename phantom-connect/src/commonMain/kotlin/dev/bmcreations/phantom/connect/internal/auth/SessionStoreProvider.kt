package dev.bmcreations.phantom.connect.internal.auth

import dev.bmcreations.phantom.connect.PhantomSession

internal interface SessionStoreProvider {
    suspend fun save(session: PhantomSession)
    suspend fun load(): PhantomSession?
    suspend fun clear()
    suspend fun saveShouldClearPreviousSession(shouldClear: Boolean) {}
    suspend fun loadShouldClearPreviousSession(): Boolean = false
}
