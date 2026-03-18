package dev.bmcreations.phantom.connect.wallet

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred

/**
 * Transparent activity that receives deeplink callbacks from the Phantom wallet app.
 *
 * During a multi-hop ceremony ([stayAlive] = true), this activity stays alive as an
 * invisible overlay between consecutive deeplink round-trips so the host app never
 * flashes between hops. Subsequent callbacks arrive via [onNewIntent] (singleTask).
 *
 * Register in your AndroidManifest.xml with an intent filter matching your callback scheme:
 * ```xml
 * <activity
 *     android:name="dev.bmcreations.phantom.connect.wallet.PhantomWalletCallbackActivity"
 *     android:exported="true"
 *     android:launchMode="singleTask"
 *     android:theme="@android:style/Theme.Translucent.NoTitleBar">
 *     <intent-filter>
 *         <action android:name="android.intent.action.VIEW" />
 *         <category android:name="android.intent.category.DEFAULT" />
 *         <category android:name="android.intent.category.BROWSABLE" />
 *         <data android:scheme="YOUR_SCHEME" android:host="phantom-wallet-callback" />
 *     </intent-filter>
 * </activity>
 * ```
 */
class PhantomWalletCallbackActivity : Activity() {

    companion object {
        internal var pendingResult: CompletableDeferred<DeeplinkResult>? = null

        /**
         * When true, the activity stays alive after handling a callback so it can
         * receive subsequent callbacks via [onNewIntent] without the host activity
         * becoming visible in between.
         */
        internal var stayAlive = false

        private var currentInstance: PhantomWalletCallbackActivity? = null

        /** Dismiss the overlay activity (called when the ceremony ends). */
        internal fun dismiss() {
            currentInstance?.let {
                it.finish()
                @Suppress("DEPRECATION")
                it.overridePendingTransition(0, 0)
            }
            currentInstance = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this
        handleIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val uri = intent?.data
        if (uri != null) {
            val params = parseQueryParams(uri)
            pendingResult?.complete(DeeplinkResult.Success(params))
        } else {
            pendingResult?.complete(DeeplinkResult.Cancelled("No data in callback"))
        }
        pendingResult = null

        if (!stayAlive) {
            currentInstance = null
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onDestroy() {
        if (currentInstance == this) currentInstance = null
        super.onDestroy()
    }

    private fun parseQueryParams(uri: Uri): Map<String, String> {
        val params = mutableMapOf<String, String>()
        for (key in uri.queryParameterNames) {
            uri.getQueryParameter(key)?.let { params[key] = it }
        }
        return params
    }
}
