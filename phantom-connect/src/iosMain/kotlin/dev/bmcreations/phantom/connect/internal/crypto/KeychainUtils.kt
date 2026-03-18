package dev.bmcreations.phantom.connect.internal.crypto

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.setObject

/**
 * Helper to set a value in an NSMutableDictionary using a CFString key.
 * The Security framework constants (kSecClass, kSecAttrService, etc.) are CFStringRef,
 * which bridge to NSString in ObjC and are treated as CPointer<__CFString> in K/N.
 * We cast the key to NSString (toll-free bridged) to use setObject:forKey:.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun NSMutableDictionary.set(key: Any?, value: Any?) {
    if (key != null && value != null) {
        @Suppress("UNCHECKED_CAST")
        this.setObject(value, forKey = key as platform.Foundation.NSCopyingProtocol)
    }
}
