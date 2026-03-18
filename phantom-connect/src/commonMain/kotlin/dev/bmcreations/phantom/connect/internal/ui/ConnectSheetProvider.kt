package dev.bmcreations.phantom.connect.internal.ui

import dev.bmcreations.phantom.connect.AuthProvider
import dev.bmcreations.phantom.connect.ConnectResult
import dev.bmcreations.phantom.connect.ConnectSheetTheme
import dev.bmcreations.phantom.connect.PhantomSession
import dev.bmcreations.phantom.connect.WalletConnector

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
     * @param session Existing session (shows connected state if non-null).
     * @param connectors External wallet connectors to display (e.g. Phantom app).
     * @param onConnect Called when the user selects a social provider. The sheet stays
     *   open while this suspends. Return [ConnectResult.Success] to close the
     *   sheet, or an error/cancellation to display in-sheet and allow retry.
     * @param onWalletConnect Called when the user selects an external wallet connector.
     * @param onDisconnect Called when the user taps disconnect.
     * @return The final [ConnectResult] — either a successful connection or
     *   a cancellation if the user dismisses the sheet.
     */
    suspend fun show(
        theme: ConnectSheetTheme = ConnectSheetTheme.Dark,
        session: PhantomSession? = null,
        providers: List<AuthProvider> = AuthProvider.all,
        connectors: List<WalletConnector> = emptyList(),
        connectorAvailability: Map<String, Boolean> = emptyMap(),
        onConnect: suspend (AuthProvider) -> ConnectResult,
        onWalletConnect: suspend (WalletConnector) -> ConnectResult = { ConnectResult.Cancelled("No wallet connector handler") },
        onDisconnect: (suspend () -> Unit)? = null,
    ): ConnectResult
}
