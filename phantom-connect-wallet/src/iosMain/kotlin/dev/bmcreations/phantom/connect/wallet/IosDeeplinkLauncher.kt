@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package dev.bmcreations.phantom.connect.wallet

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.UIKit.UIApplication

/**
 * iOS [DeeplinkLauncher] that opens the Phantom app via UIApplication.open()
 * and waits for the callback via universal link.
 *
 * Your app must handle the callback URL in your AppDelegate or SceneDelegate
 * and call [IosDeeplinkLauncher.handleCallback] with the received URL.
 */
class IosDeeplinkLauncher : DeeplinkLauncher {

    companion object {
        private var pendingResult: CompletableDeferred<DeeplinkResult>? = null

        /**
         * Call this from your AppDelegate/SceneDelegate when a universal link
         * or custom scheme URL is received matching your callback scheme.
         */
        fun handleCallback(url: String) {
            val components = NSURLComponents(string = url) ?: run {
                pendingResult?.complete(DeeplinkResult.Error(IllegalStateException("Invalid callback URL: $url")))
                pendingResult = null
                return
            }

            val params = mutableMapOf<String, String>()
            components.queryItems?.forEach { item ->
                val queryItem = item as platform.Foundation.NSURLQueryItem
                val name = queryItem.name
                // NSURLQueryItem does percent-decoding but not form-encoded + → space
                val value = queryItem.value?.replace("+", " ")
                if (value != null) {
                    params[name] = value
                }
            }

            pendingResult?.complete(DeeplinkResult.Success(params))
            pendingResult = null
        }

        /** Call this if the user returns to the app without completing the flow. */
        fun handleCancellation() {
            pendingResult?.complete(DeeplinkResult.Cancelled("User returned without completing"))
            pendingResult = null
        }
    }

    override suspend fun launch(url: String, callbackScheme: String): DeeplinkResult {
        return withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<DeeplinkResult>()
            pendingResult = deferred

            val nsUrl = NSURL(string = url)
            UIApplication.sharedApplication.openURL(
                nsUrl,
                options = emptyMap<Any?, Any>(),
                completionHandler = { success ->
                    if (!success) {
                        pendingResult = null
                        deferred.complete(
                            DeeplinkResult.Error(
                                IllegalStateException("Cannot open Phantom app. Is it installed?")
                            )
                        )
                    }
                }
            )

            deferred.await()
        }
    }

    override suspend fun isAppInstalled(): Boolean {
        // canOpenURL requires the consumer to declare "phantom" in LSApplicationQueriesSchemes.
        // Without that plist entry, canOpenURL always returns false regardless of install state.
        // Since we can't enforce plist entries from an SDK, we always return true (optimistic)
        // and let launch() handle the "not installed" case with a proper error.
        return true
    }
}
