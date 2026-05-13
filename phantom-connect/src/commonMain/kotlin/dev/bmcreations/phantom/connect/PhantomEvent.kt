package dev.bmcreations.phantom.connect

import dev.bmcreations.phantom.connect.internal.network.SpendingLimitError

/**
 * Events emitted by the Phantom SDK during connect, disconnect, and signing flows.
 *
 * Observe via [PhantomSdk.events]:
 * ```kotlin
 * sdk.events.collect { event ->
 *     when (event) {
 *         is PhantomEvent.Connected -> println("Connected: ${event.session.walletId}")
 *         is PhantomEvent.ConnectError -> println("Error: ${event.error.message}")
 *         is PhantomEvent.Disconnected -> println("Disconnected")
 *         // ...
 *     }
 * }
 * ```
 */
sealed class PhantomEvent {
    /** Emitted when a connect flow starts (before browser launch). */
    data class ConnectStart(val provider: String, val source: String) : PhantomEvent()

    /** Emitted when a connect flow completes successfully. */
    data class Connected(val session: PhantomSession, val source: String) : PhantomEvent()

    /** Emitted when a connect flow fails. */
    data class ConnectError(val error: Throwable, val source: String) : PhantomEvent()

    /** Emitted when the user disconnects (logout). */
    data class Disconnected(val source: String) : PhantomEvent()

    /** Emitted when a spending limit is reached during transaction signing. */
    data class SpendingLimitReached(val error: SpendingLimitError) : PhantomEvent()
}
