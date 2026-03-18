package dev.bmcreations.phantom.connect.internal.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import dev.bmcreations.phantom.connect.AuthProvider
import dev.bmcreations.phantom.connect.ConnectResult
import dev.bmcreations.phantom.connect.ConnectSheetTheme
import dev.bmcreations.phantom.connect.PhantomSession
import dev.bmcreations.phantom.connect.WalletConnector
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

internal class PhantomConnectSheetActivity : ComponentActivity() {

    companion object {
        var pendingResult: CompletableDeferred<ConnectResult>? = null
        var pendingTheme: ConnectSheetTheme = ConnectSheetTheme.Dark
        var pendingSession: PhantomSession? = null
        var pendingProviders: List<AuthProvider> = AuthProvider.all
        var pendingConnectors: List<WalletConnector> = emptyList()
        var pendingConnectorAvailability: Map<String, Boolean> = emptyMap()
        var pendingOnConnect: (suspend (AuthProvider) -> ConnectResult)? = null
        var pendingOnWalletConnect: (suspend (WalletConnector) -> ConnectResult)? = null
        var pendingOnDisconnect: (suspend () -> Unit)? = null
    }

    private fun complete(result: ConnectResult) {
        pendingResult?.complete(result)
        pendingResult = null
        pendingOnConnect = null
        pendingOnWalletConnect = null
        pendingOnDisconnect = null
        pendingSession = null
        pendingProviders = AuthProvider.all
        pendingConnectors = emptyList()
        pendingConnectorAvailability = emptyMap()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (pendingResult == null) {
            finish()
            return
        }

        val theme = pendingTheme
        val session = pendingSession
        val providers = pendingProviders
        val connectors = pendingConnectors
        val connectorAvailability = pendingConnectorAvailability
        val onConnect = pendingOnConnect ?: run {
            complete(ConnectResult.Cancelled("No connect handler"))
            return
        }
        val onWalletConnect = pendingOnWalletConnect ?: { ConnectResult.Cancelled("No wallet connect handler") }
        val onDisconnect = pendingOnDisconnect

        setContent {
            val scope = rememberCoroutineScope()
            var error by remember { mutableStateOf<String?>(null) }
            var loadingChoice by remember { mutableStateOf<ConnectChoice?>(null) }

            PhantomConnectSheet(
                theme = theme,
                session = session,
                error = error,
                loadingChoice = loadingChoice,
                providers = providers,
                connectors = connectors,
                connectorAvailability = connectorAvailability,
                onProviderSelected = { provider ->
                    if (loadingChoice == null) {
                        loadingChoice = ConnectChoice.Social(provider)
                        error = null
                        scope.launch {
                            val result = onConnect(provider)
                            when (result) {
                                is ConnectResult.Success -> {
                                    // Clear Chrome Custom Tab from back stack
                                    startActivity(
                                        Intent(this@PhantomConnectSheetActivity, this@PhantomConnectSheetActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        }
                                    )
                                    complete(result)
                                }
                                is ConnectResult.Cancelled -> {
                                    error = "Authentication error: ${result.reason ?: "User cancelled authentication"}"
                                    loadingChoice = null
                                }
                                is ConnectResult.Error -> {
                                    error = "Authentication error: ${result.cause.message ?: "Unknown error"}"
                                    loadingChoice = null
                                }
                            }
                        }
                    }
                },
                onConnectorSelected = { connector ->
                    if (loadingChoice == null) {
                        loadingChoice = ConnectChoice.ExternalWallet(connector)
                        error = null
                        scope.launch {
                            val result = onWalletConnect(connector)
                            when (result) {
                                is ConnectResult.Success -> complete(result)
                                is ConnectResult.Cancelled -> {
                                    error = "Connection error: ${result.reason ?: "User cancelled"}"
                                    loadingChoice = null
                                }
                                is ConnectResult.Error -> {
                                    error = "Connection error: ${result.cause.message ?: "Unknown error"}"
                                    loadingChoice = null
                                }
                            }
                        }
                    }
                },
                onDisconnect = {
                    scope.launch {
                        onDisconnect?.invoke()
                        complete(ConnectResult.Cancelled("User disconnected"))
                    }
                },
                onDismiss = {
                    complete(
                        if (error != null) ConnectResult.Error(Exception(error))
                        else ConnectResult.Cancelled("User dismissed connect sheet")
                    )
                },
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingResult?.complete(ConnectResult.Cancelled("Sheet destroyed"))
        pendingResult = null
        pendingOnConnect = null
        pendingOnWalletConnect = null
        pendingOnDisconnect = null
        pendingSession = null
        pendingProviders = AuthProvider.all
        pendingConnectors = emptyList()
    }
}
