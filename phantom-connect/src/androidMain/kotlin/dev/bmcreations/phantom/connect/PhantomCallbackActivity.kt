package dev.bmcreations.phantom.connect

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.bmcreations.phantom.connect.internal.PhantomPendingRedirect
import kotlinx.coroutines.CompletableDeferred

/**
 * Transparent, singleTask Activity that catches the Phantom OAuth redirect.
 *
 * Declare in your AndroidManifest.xml:
 * ```xml
 * <activity
 *     android:name="dev.bmcreations.phantom.connect.PhantomCallbackActivity"
 *     android:launchMode="singleTask"
 *     android:theme="@android:style/Theme.Translucent.NoTitleBar"
 *     android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.intent.action.VIEW" />
 *         <category android:name="android.intent.category.DEFAULT" />
 *         <category android:name="android.intent.category.BROWSABLE" />
 *         <data android:scheme="yourapp" android:host="phantom-callback" />
 *     </intent-filter>
 * </activity>
 * ```
 */
class PhantomCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null) {
            val params = mutableMapOf<String, String>()
            uri.queryParameterNames.forEach { key ->
                uri.getQueryParameter(key)?.let { value ->
                    params[key] = value
                }
            }

            val pending = pendingResult
            if (pending != null && pending.isActive) {
                pending.complete(OAuthResult.Success(params))
            } else {
                // Process may have been killed; park the URI for later pickup
                PhantomPendingRedirect.store(uri)
            }
        } else {
            pendingResult?.takeIf { it.isActive }?.complete(
                OAuthResult.Cancelled("No redirect URI received")
            )
        }
    }

    companion object {
        /**
         * The currently pending OAuth result deferred.
         * Set by [AndroidOAuthLauncher] before opening Custom Tabs.
         */
        @Volatile
        internal var pendingResult: CompletableDeferred<OAuthResult>? = null
    }
}
