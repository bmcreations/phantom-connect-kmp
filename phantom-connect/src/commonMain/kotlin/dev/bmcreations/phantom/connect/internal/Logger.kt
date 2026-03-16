package dev.bmcreations.phantom.connect.internal

import dev.bmcreations.phantom.connect.LogLevel
import dev.bmcreations.phantom.connect.PhantomLogger

/** Internal singleton that forwards log calls to the user-provided [PhantomLogger], if any. */
internal object SdkLogger {
    var logger: PhantomLogger? = null

    fun debug(tag: String, message: String) = logger?.log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: String) = logger?.log(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String) = logger?.log(LogLevel.WARN, tag, message)
    fun error(tag: String, message: String) = logger?.log(LogLevel.ERROR, tag, message)
}
