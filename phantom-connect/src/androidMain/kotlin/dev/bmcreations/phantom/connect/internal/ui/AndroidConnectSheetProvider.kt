package dev.bmcreations.phantom.connect.internal.ui

import android.content.Intent
import dev.bmcreations.phantom.connect.internal.platform.PhantomSdkInitializer
import dev.bmcreations.phantom.connect.internal.platform.SdkLogger
import dev.bmcreations.phantom.connect.AuthProvider
import dev.bmcreations.phantom.connect.ConnectResult
import dev.bmcreations.phantom.connect.ConnectSheetTheme
import dev.bmcreations.phantom.connect.PhantomSession
import dev.bmcreations.phantom.connect.WalletConnector
import kotlinx.coroutines.CompletableDeferred

internal class AndroidConnectSheetProvider : ConnectSheetProvider {

    override suspend fun show(
        theme: ConnectSheetTheme,
        session: PhantomSession?,
        providers: List<AuthProvider>,
        connectors: List<WalletConnector>,
        connectorAvailability: Map<String, Boolean>,
        onConnect: suspend (AuthProvider) -> ConnectResult,
        onWalletConnect: suspend (WalletConnector) -> ConnectResult,
        onDisconnect: (suspend () -> Unit)?,
    ): ConnectResult {
        SdkLogger.debug("ConnectSheet", "Showing Android connect sheet")
        val deferred = CompletableDeferred<ConnectResult>()
        PhantomConnectSheetActivity.pendingResult = deferred
        PhantomConnectSheetActivity.pendingTheme = theme
        PhantomConnectSheetActivity.pendingSession = session
        PhantomConnectSheetActivity.pendingProviders = providers
        PhantomConnectSheetActivity.pendingConnectors = connectors
        PhantomConnectSheetActivity.pendingConnectorAvailability = connectorAvailability
        PhantomConnectSheetActivity.pendingOnConnect = onConnect
        PhantomConnectSheetActivity.pendingOnWalletConnect = onWalletConnect
        PhantomConnectSheetActivity.pendingOnDisconnect = onDisconnect

        val context = PhantomSdkInitializer.requireContext()
        val intent = Intent(context, PhantomConnectSheetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        return deferred.await()
    }
}
