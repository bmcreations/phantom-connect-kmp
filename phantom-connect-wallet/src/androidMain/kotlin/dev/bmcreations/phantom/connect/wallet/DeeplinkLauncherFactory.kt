package dev.bmcreations.phantom.connect.wallet

import android.content.Context

/**
 * Creates an instance of [DeeplinkLauncher] for the specified [context].
 *
 * @param context The Android [Context] (application or activity) required to launch deeplinks.
 * @return A platform-specific implementation of [DeeplinkLauncher].
 */
fun createDeeplinkLauncher(context: Context): DeeplinkLauncher = AndroidDeeplinkLauncher(context)
