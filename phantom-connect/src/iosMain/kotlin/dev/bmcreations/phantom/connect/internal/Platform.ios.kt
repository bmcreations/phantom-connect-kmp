package dev.bmcreations.phantom.connect.internal

import platform.Foundation.NSNumber
import platform.Foundation.valueForKey
import platform.UIKit.UIDevice
import platform.UIKit.UIScreen

internal actual fun platformKeyStore(): Ed25519KeyStoreProvider = IosEd25519KeyStore()
internal actual fun platformSessionStore(): SessionStoreProvider = IosSessionStore()
internal actual fun platformConnectSheetProvider(): ConnectSheetProvider = IosConnectSheetProvider()
internal actual fun getPlatform(): String = "ios"

internal actual fun initPlatform(context: Any) {
    // No-op on iOS; no Context required.
}

internal actual val sdkType: String = "react-native" // hack to work around validation on server

val iosVersion
    get() = UIDevice.currentDevice.systemVersion
val majorVersion
    get() = iosVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
val isIos26
    get() = majorVersion >= 19

/**
 * The actual device display corner radius from UIScreen's private `_displayCornerRadius`.
 * Falls back to 55.0 (modern iPhone default) if unavailable (e.g. simulator).
 */
val displayCornerRadius: Double
    get() = try {
        (UIScreen.mainScreen.valueForKey("_displayCornerRadius") as? NSNumber)?.doubleValue ?: 55.0
    } catch (_: Exception) {
        55.0
    }
