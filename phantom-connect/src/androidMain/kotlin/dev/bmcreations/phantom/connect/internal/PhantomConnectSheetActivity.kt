package dev.bmcreations.phantom.connect.internal

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import dev.bmcreations.phantom.connect.AuthProvider
import dev.bmcreations.phantom.connect.ConnectResult
import dev.bmcreations.phantom.connect.ConnectSheetTheme
import dev.bmcreations.phantom.connect.PhantomSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

internal class PhantomConnectSheetActivity : ComponentActivity() {

    companion object {
        var pendingResult: CompletableDeferred<ConnectResult>? = null
        var pendingTheme: ConnectSheetTheme = ConnectSheetTheme.Dark
        var pendingSession: PhantomSession? = null
        var pendingOnConnect: (suspend (AuthProvider) -> ConnectResult)? = null
        var pendingOnDisconnect: (suspend () -> Unit)? = null
    }

    private fun complete(result: ConnectResult) {
        pendingResult?.complete(result)
        pendingResult = null
        pendingOnConnect = null
        pendingOnDisconnect = null
        pendingSession = null
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
        val onConnect = pendingOnConnect ?: run {
            complete(ConnectResult.Cancelled("No connect handler"))
            return
        }
        val onDisconnect = pendingOnDisconnect

        setContent {
            val scope = rememberCoroutineScope()
            var error by remember { mutableStateOf<String?>(null) }
            var loadingProvider by remember { mutableStateOf<AuthProvider?>(null) }

            PhantomConnectSheet(
                theme = theme,
                session = session,
                error = error,
                loadingProvider = loadingProvider,
                onProviderSelected = { provider ->
                    if (loadingProvider == null) {
                        loadingProvider = provider
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
                                    loadingProvider = null
                                }
                                is ConnectResult.Error -> {
                                    error = "Authentication error: ${result.cause.message ?: "Unknown error"}"
                                    loadingProvider = null
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
        pendingOnDisconnect = null
        pendingSession = null
    }
}
