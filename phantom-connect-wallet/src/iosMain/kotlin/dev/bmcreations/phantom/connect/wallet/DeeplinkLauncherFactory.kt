package dev.bmcreations.phantom.connect.wallet

/**
 * Creates an instance of [DeeplinkLauncher] for iOS.
 *
 * @return An [IosDeeplinkLauncher] that opens deeplinks via UIApplication
 *   and receives callbacks via [IosDeeplinkLauncher.handleCallback].
 */
fun createDeeplinkLauncher(): DeeplinkLauncher = IosDeeplinkLauncher()
