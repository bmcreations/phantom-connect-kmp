package dev.bmcreations.phantom.connect.internal

import dev.bmcreations.phantom.connect.AuthProvider
import dev.bmcreations.phantom.connect.ConnectResult
import dev.bmcreations.phantom.connect.ConnectSheetTheme
import dev.bmcreations.phantom.connect.PhantomSession

/**
 * Platform-specific provider that shows a connect sheet and drives the full
 * connect lifecycle. The sheet stays open during OAuth — on success it closes,
 * on error/cancellation it shows the error and lets the user retry.
 */
internal interface ConnectSheetProvider {
    /**
     * Show the connect sheet and handle the full connect flow.
     *
     * @param theme Visual theme for the sheet.
     * @param onConnect Called when the user selects a provider. The sheet stays
     *   open while this suspends. Return [ConnectResult.Success] to close the
     *   sheet, or an error/cancellation to display in-sheet and allow retry.
     * @return The final [ConnectResult] — either a successful connection or
     *   a cancellation if the user dismisses the sheet.
     */
    suspend fun show(
        theme: ConnectSheetTheme = ConnectSheetTheme.Dark,
        session: PhantomSession? = null,
        onConnect: suspend (AuthProvider) -> ConnectResult,
        onDisconnect: (suspend () -> Unit)? = null,
    ): ConnectResult
}
