package dev.bmcreations.phantom.connect.internal.auth

import android.content.Context
import android.content.SharedPreferences
import dev.bmcreations.phantom.connect.PhantomSession
import dev.bmcreations.phantom.connect.internal.storage.EncryptedPrefs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class AndroidSessionStore private constructor(
    private val prefs: SharedPreferences,
) : SessionStoreProvider {

    companion object {
        private const val PREFS_FILE = "phantom_connect_session"
        private const val KEY_SESSION = "session"
        private const val KEY_SHOULD_CLEAR = "should_clear_previous_session"

        fun create(context: Context): AndroidSessionStore {
            return AndroidSessionStore(EncryptedPrefs.create(context, PREFS_FILE))
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(session: PhantomSession) {
        prefs.edit().putString(KEY_SESSION, json.encodeToString(session)).apply()
    }

    override suspend fun load(): PhantomSession? {
        return try {
            // getString performs the AES-GCM value decrypt, so a per-value tag failure must be
            // caught here too — treat any read/parse failure as "no session".
            val raw = prefs.getString(KEY_SESSION, null) ?: return null
            json.decodeFromString<PhantomSession>(raw)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun clear() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    override suspend fun saveShouldClearPreviousSession(shouldClear: Boolean) {
        prefs.edit().putBoolean(KEY_SHOULD_CLEAR, shouldClear).apply()
    }

    override suspend fun loadShouldClearPreviousSession(): Boolean {
        return try {
            prefs.getBoolean(KEY_SHOULD_CLEAR, false)
        } catch (_: Exception) {
            false
        }
    }
}
