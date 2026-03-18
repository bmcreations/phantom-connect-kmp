package dev.bmcreations.phantom.connect.internal.ui

import android.net.Uri

/**
 * Singleton that parks the redirect URI when the process is killed during
 * an OAuth flow. The hosting Activity should check [consume] in onResume
 * to pick up any pending redirect that arrived while the process was dead.
 */
internal object PhantomPendingRedirect {
    @Volatile
    private var pendingUri: Uri? = null

    /** Store a redirect URI that arrived while no listener was registered. */
    internal fun store(uri: Uri) {
        pendingUri = uri
    }

    /**
     * Consume and return the pending redirect URI, if any.
     * Returns null if there is no pending redirect.
     */
    fun consume(): Uri? {
        val uri = pendingUri
        pendingUri = null
        return uri
    }
}
