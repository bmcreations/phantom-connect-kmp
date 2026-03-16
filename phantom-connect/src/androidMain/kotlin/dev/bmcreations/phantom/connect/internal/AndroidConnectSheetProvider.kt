package dev.bmcreations.phantom.connect.internal

import android.content.Intent
import dev.bmcreations.phantom.connect.AuthProvider
import dev.bmcreations.phantom.connect.ConnectResult
import dev.bmcreations.phantom.connect.ConnectSheetTheme
import dev.bmcreations.phantom.connect.PhantomSession
import kotlinx.coroutines.CompletableDeferred

internal class AndroidConnectSheetProvider : ConnectSheetProvider {

    override suspend fun show(
        theme: ConnectSheetTheme,
        session: PhantomSession?,
        onConnect: suspend (AuthProvider) -> ConnectResult,
        onDisconnect: (suspend () -> Unit)?,
    ): ConnectResult {
        SdkLogger.debug("ConnectSheet", "Showing Android connect sheet")
        val deferred = CompletableDeferred<ConnectResult>()
        PhantomConnectSheetActivity.pendingResult = deferred
        PhantomConnectSheetActivity.pendingTheme = theme
        PhantomConnectSheetActivity.pendingSession = session
        PhantomConnectSheetActivity.pendingOnConnect = onConnect
        PhantomConnectSheetActivity.pendingOnDisconnect = onDisconnect

        val context = PhantomSdkInitializer.requireContext()
        val intent = Intent(context, PhantomConnectSheetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        return deferred.await()
    }
}
