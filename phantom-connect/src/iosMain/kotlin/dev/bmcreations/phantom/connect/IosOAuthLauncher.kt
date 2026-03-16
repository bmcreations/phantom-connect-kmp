package dev.bmcreations.phantom.connect

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorDomain
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosOAuthLauncher : OAuthLauncher {

    override suspend fun launch(url: String, callbackScheme: String): OAuthResult {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val nsUrl = NSURL(string = url)

                var sessionRef: ASWebAuthenticationSession? = null

                val session = ASWebAuthenticationSession(
                    uRL = nsUrl,
                    callbackURLScheme = callbackScheme,
                    completionHandler = { callbackURL: NSURL?, error: NSError? ->
                        sessionRef = null

                        val result = when {
                            error != null -> {
                                if (error.domain == ASWebAuthenticationSessionErrorDomain && error.code == 1L) {
                                    OAuthResult.Cancelled(reason = "User cancelled the authentication session")
                                } else {
                                    OAuthResult.Error(
                                        cause = Exception("ASWebAuthenticationSession error: ${error.localizedDescription} (code ${error.code})")
                                    )
                                }
                            }
                            callbackURL != null -> {
                                val params = parseQueryParameters(callbackURL)
                                OAuthResult.Success(params = params)
                            }
                            else -> {
                                OAuthResult.Error(cause = Exception("No callback URL and no error returned"))
                            }
                        }

                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }
                )

                session.prefersEphemeralWebBrowserSession = false

                val contextProvider = PresentationContextProvider()
                session.presentationContextProvider = contextProvider

                sessionRef = session

                continuation.invokeOnCancellation {
                    session.cancel()
                    sessionRef = null
                }

                session.start()
            }
        }
    }

    private fun parseQueryParameters(url: NSURL): Map<String, String> {
        val components = NSURLComponents(uRL = url, resolvingAgainstBaseURL = false)
        val queryItems = components.queryItems ?: return emptyMap()
        val params = mutableMapOf<String, String>()
        for (item in queryItems) {
            val queryItem = item as NSURLQueryItem
            params[queryItem.name] = queryItem.value ?: ""
        }
        return params
    }
}

private class PresentationContextProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession
    ): ASPresentationAnchor {
        @Suppress("UNCHECKED_CAST")
        val scenes = UIApplication.sharedApplication.connectedScenes as Set<*>
        val keyWindow = scenes
            .mapNotNull { scene ->
                (scene as? UIWindowScene)?.windows
                    ?.filterIsInstance<UIWindow>()
                    ?.firstOrNull { it.isKeyWindow() }
            }
            .firstOrNull()

        return keyWindow ?: UIWindow()
    }
}
