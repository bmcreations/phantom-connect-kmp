package dev.bmcreations.phantom.connect.internal.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.bmcreations.phantom.connect.PhantomSession
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
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

            return AndroidSessionStore(prefs)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(session: PhantomSession) {
        prefs.edit().putString(KEY_SESSION, json.encodeToString(session)).apply()
    }

    override suspend fun load(): PhantomSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
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
        return prefs.getBoolean(KEY_SHOULD_CLEAR, false)
    }
}
