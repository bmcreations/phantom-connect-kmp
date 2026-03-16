@file:OptIn(ExperimentalForeignApi::class)

package dev.bmcreations.phantom.connect.internal

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData

/**
 * A builder for CoreFoundation dictionaries used with the iOS Security framework.
 *
 * In Kotlin/Native, toll-free bridging between NSDictionary and CFDictionaryRef doesn't
 * work automatically. CF constant keys (kSecClass, kSecAttrService, etc.) are raw pointers,
 * not ObjC objects, so NSMutableDictionary.set() can't use them as keys. And
 * `NSMutableDictionary as CFDictionaryRef` fails with ClassCastException.
 *
 * This builder uses CFDictionaryCreateMutable / CFDictionarySetValue directly,
 * which accept raw CFTypeRef pointers natively.
 */
internal class CFDictionaryBuilder {
    private val entries = mutableListOf<Pair<COpaquePointer, COpaquePointer>>()

    fun set(key: CFTypeRef?, value: CFTypeRef?): CFDictionaryBuilder {
        if (key != null && value != null) {
            entries.add(key to value)
        }
        return this
    }

    fun set(key: CFTypeRef?, value: String): CFDictionaryBuilder {
        if (key != null) {
            val cfStr = CFBridgingRetain(value) ?: return this
            entries.add(key to cfStr)
        }
        return this
    }

    fun set(key: CFTypeRef?, value: Boolean): CFDictionaryBuilder {
        if (key != null && value) {
            entries.add(key to kCFBooleanTrue!!.reinterpret<cnames.structs.__CFBoolean>())
        }
        return this
    }

    fun set(key: CFTypeRef?, value: NSData): CFDictionaryBuilder {
        if (key != null) {
            val cfData = CFBridgingRetain(value) ?: return this
            entries.add(key to cfData)
        }
        return this
    }

    fun build(): CFMutableDictionaryRef = kotlinx.cinterop.memScoped {
        val dict = CFDictionaryCreateMutable(
            null,
            entries.size.toLong(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: error("CFDictionaryCreateMutable returned null")

        for ((key, value) in entries) {
            CFDictionarySetValue(dict, key, value)
        }
        dict
    }
}

/**
 * Build a CF dictionary and pass it to [block], releasing it when done.
 */
internal inline fun <R> withCFDictionary(
    builder: CFDictionaryBuilder.() -> Unit,
    block: (CFDictionaryRef) -> R,
): R {
    val dict = CFDictionaryBuilder().apply(builder).build()
    try {
        return block(dict)
    } finally {
        CFRelease(dict)
    }
}
