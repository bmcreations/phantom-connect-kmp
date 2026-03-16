package dev.bmcreations.phantom.connect.sample

import android.app.Activity
import dev.bmcreations.phantom.connect.AndroidOAuthLauncher
import dev.bmcreations.phantom.connect.OAuthLauncher

/**
 * Creates an instance of [OAuthLauncher] for the specified [activity].
 *
 * @param activity The [Activity] context required to initialize the OAuth launcher.
 * @return A platform-specific implementation of [OAuthLauncher].
 */
fun createOAuthLauncher(activity: Activity): OAuthLauncher = AndroidOAuthLauncher(activity)
