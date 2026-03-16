package dev.bmcreations.phantom.connect.internal

import dev.bmcreations.phantom.connect.PhantomSession

internal interface SessionStoreProvider {
    suspend fun save(session: PhantomSession)
    suspend fun load(): PhantomSession?
    suspend fun clear()
}
