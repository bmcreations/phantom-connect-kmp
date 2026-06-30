package dev.bmcreations.phantom.connect.internal.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

/**
 * Builds [EncryptedSharedPreferences] that self-heal when the AndroidKeyStore master key and the
 * on-disk Tink keyset fall out of sync.
 *
 * That desync produces an uncaught `javax.crypto.AEADBadTagException`
 * (Keystore `VERIFICATION_FAILED`) from Tink's AES-GCM decrypt the moment the store is opened or a
 * value is read. It happens when app data is restored from a cloud backup or device-to-device
 * transfer (AndroidKeyStore keys are hardware-bound and never migrate), when the key is
 * invalidated, or when a keyset is written partially. The encrypted data here (Phantom session,
 * ephemeral key material) is fully recoverable — the user simply re-connects — so on corruption we
 * wipe and rebuild rather than crash.
 */
internal object EncryptedPrefs {

    /**
     * Returns an [EncryptedSharedPreferences] for [fileName], recovering from a corrupt
     * keyset/master key if necessary.
     *
     * Recovery is two-tier to minimise collateral damage, since all stores share the default master
     * key alias:
     *  1. Delete just this prefs file (the per-file Tink keyset lives inside it) and retry.
     *  2. If that still fails, the master key itself is bad — delete it so [MasterKey.Builder]
     *     regenerates it, wipe the file again, and retry. Sibling stores re-heal the same way on
     *     their next open.
     */
    fun create(context: Context, fileName: String): SharedPreferences {
        return createWithRecovery(
            build = { build(context, fileName) },
            wipePrefs = { deletePrefsFile(context, fileName) },
            wipeMasterKey = { deleteMasterKey() },
        )
    }

    /**
     * Two-tier recovery control flow, extracted from Android framework calls so it can be unit
     * tested without a real keystore:
     *  1. [build]; on failure [wipePrefs] and rebuild.
     *  2. On a second failure [wipeMasterKey] + [wipePrefs] and rebuild once more.
     * A third failure propagates.
     */
    internal fun <T> createWithRecovery(
        build: () -> T,
        wipePrefs: () -> Unit,
        wipeMasterKey: () -> Unit,
    ): T {
        return try {
            build()
        } catch (first: Throwable) {
            // Tier 1: assume the per-file keyset is corrupt.
            wipePrefs()
            try {
                build()
            } catch (second: Throwable) {
                // Tier 2: the shared master key is bad.
                wipeMasterKey()
                wipePrefs()
                build()
            }
        }
    }

    private fun build(context: Context, fileName: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun deletePrefsFile(context: Context, fileName: String) {
        // minSdk 24, so deleteSharedPreferences is always available.
        runCatching { context.deleteSharedPreferences(fileName) }
    }

    private fun deleteMasterKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    }

    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
}
