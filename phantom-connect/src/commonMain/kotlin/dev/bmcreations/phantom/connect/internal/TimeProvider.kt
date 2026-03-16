package dev.bmcreations.phantom.connect.internal

import kotlin.time.Clock
import kotlin.time.Instant


internal interface TimeProvider {
    fun now(): Instant
}

internal class SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Clock.System.now()
}
