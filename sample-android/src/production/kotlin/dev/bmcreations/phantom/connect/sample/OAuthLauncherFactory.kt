package dev.bmcreations.phantom.connect.sample

import android.app.Activity
import dev.bmcreations.phantom.connect.OAuthLauncher
import dev.bmcreations.phantom.connect.createOAuthLauncher

/**
 * Creates an instance of [OAuthLauncher] for the specified [activity].
 *
 * @param activity The [Activity] context required to initialize the OAuth launcher.
 * @return A platform-specific implementation of [OAuthLauncher].
 */
fun createOAuthLauncher(activity: Activity): OAuthLauncher = createOAuthLauncher(activity)
