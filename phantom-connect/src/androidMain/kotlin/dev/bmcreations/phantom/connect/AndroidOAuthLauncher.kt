package dev.bmcreations.phantom.connect

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CompletableDeferred

/**
 * Android implementation of [OAuthLauncher] using Custom Tabs.
 *
 * The flow:
 * 1. Opens the OAuth URL in a Chrome Custom Tab.
 * 2. Phantom redirects back to `callbackScheme://phantom-callback?...`
 * 3. [PhantomCallbackActivity] catches the redirect and completes the deferred.
 * 4. If the user dismisses Custom Tabs without completing, activity resume
 *    detection completes with [OAuthResult.Cancelled].
 *
 * @param activity The activity context used to launch Custom Tabs.
 */
class AndroidOAuthLauncher(
    private val activity: Activity,
) : OAuthLauncher {

    override suspend fun launch(url: String, callbackScheme: String): OAuthResult {
        val deferred = CompletableDeferred<OAuthResult>()
        PhantomCallbackActivity.pendingResult = deferred

        var launched = false
        val handler = Handler(Looper.getMainLooper())

        // Detect when any app activity resumes after Custom Tabs was launched.
        // If the deferred is still active, the user dismissed the browser.
        // A brief delay allows PhantomCallbackActivity to process redirects first.
        val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) {
                if (launched && deferred.isActive && a !is PhantomCallbackActivity) {
                    handler.postDelayed({
                        if (deferred.isActive) {
                            deferred.complete(OAuthResult.Cancelled("User cancelled authentication"))
                        }
                    }, 300)
                }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        }

        return try {
            activity.application.registerActivityLifecycleCallbacks(lifecycleCallbacks)

            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()

            customTabsIntent.launchUrl(activity, Uri.parse(url))
            launched = true

            deferred.await()
        } catch (e: Exception) {
            deferred.cancel()
            OAuthResult.Error(e)
        } finally {
            activity.application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
            PhantomCallbackActivity.pendingResult = null
        }
    }
}
