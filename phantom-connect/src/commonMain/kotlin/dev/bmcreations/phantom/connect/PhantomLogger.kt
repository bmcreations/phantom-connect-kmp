package dev.bmcreations.phantom.connect

/** Log level for SDK diagnostic messages. */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Callback interface for receiving SDK log messages.
 *
 * Pass a lambda to [PhantomSdkConfig.logger]:
 * ```kotlin
 * PhantomSdkConfig(
 *     // ...
 *     logger = PhantomLogger { level, tag, message ->
 *         Log.d("Phantom/$tag", "[$level] $message")
 *     },
 * )
 * ```
 *
 * When no logger is set, all log calls are no-ops with zero overhead.
 */
fun interface PhantomLogger {
    fun log(level: LogLevel, tag: String, message: String)
}
