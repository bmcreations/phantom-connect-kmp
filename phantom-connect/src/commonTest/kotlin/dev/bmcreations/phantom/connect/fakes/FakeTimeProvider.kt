package dev.bmcreations.phantom.connect.fakes

import dev.bmcreations.phantom.connect.internal.TimeProvider
import kotlinx.datetime.Instant
import kotlin.time.Duration

internal class FakeTimeProvider(
    private var current: Instant = Instant.fromEpochMilliseconds(1700000000000L),
) : TimeProvider {

    override fun now(): Instant = current

    fun advanceBy(duration: Duration) {
        current = current + duration
    }

    fun setTo(instant: Instant) {
        current = instant
    }

    fun setToEpochMs(epochMs: Long) {
        current = Instant.fromEpochMilliseconds(epochMs)
    }
}
