package dev.bmcreations.phantom.connect.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.bmcreations.phantom.BuildConfig
import dev.bmcreations.phantom.connect.AuthProvider
import dev.bmcreations.phantom.connect.ConnectResult
import dev.bmcreations.phantom.connect.LogLevel
import dev.bmcreations.phantom.connect.PhantomClient
import dev.bmcreations.phantom.connect.PhantomSdkConfig
import dev.bmcreations.phantom.connect.PhantomSession
import dev.bmcreations.phantom.connect.sample.screens.HomeScreen
import dev.bmcreations.phantom.connect.sample.screens.WalletOperationsScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phantom = PhantomClient.create(
            config = PhantomSdkConfig(
                appId = BuildConfig.PHANTOM_APP_ID,
                redirectScheme = "phantomsample",
                redirectUri = "phantomsample://phantom-callback",
                baseUrl = BuildConfig.PHANTOM_BASE_URL,
                loginBaseUrl = BuildConfig.PHANTOM_LOGIN_BASE_URL,
                logger = { level, tag, message ->
                    when (level) {
                        LogLevel.DEBUG -> Log.d(tag, message)
                        LogLevel.INFO -> Log.i(tag, message)
                        LogLevel.WARN -> Log.w(tag, message)
                        LogLevel.ERROR -> Log.e(tag, message)
                    }
                }
            ),
            oauthLauncher = createOAuthLauncher(this),
        )

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                PhantomSampleApp(phantom)
            }
        }
    }
}

@Composable
private fun PhantomSampleApp(sdk: PhantomClient) {
    var session by remember { mutableStateOf<PhantomSession?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf("home") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            session = sdk.getSession()
        } catch (e: Exception) {
            error = "Session restore failed: ${e.message}"
        }
    }

    fun handleConnect(provider: AuthProvider? = null) {
        error = null
        scope.launch {
            val result = if (provider != null) sdk.connect(provider) else sdk.connect()
            when (result) {
                is ConnectResult.Success -> session = result.session
                is ConnectResult.Cancelled -> {
                    // Re-check session (user may have disconnected via modal)
                    session = sdk.getSession()
                    if (session == null) screen = "home"
                }
                is ConnectResult.Error -> error = result.cause.message
            }
        }
    }

    fun handleDisconnect() {
        scope.launch {
            sdk.logout()
            session = null
            error = null
            screen = "home"
        }
    }

    when (screen) {
        "home" -> HomeScreen(
            sdk = sdk,
            session = session,
            error = error,
            onConnect = { handleConnect() },
            onConnectGoogle = { handleConnect(AuthProvider.Google) },
            onOpenWallet = { screen = "wallet" },
            onDisconnect = { handleDisconnect() },
        )
        "wallet" -> WalletOperationsScreen(
            sdk = sdk,
            session = session!!,
            onBack = { screen = "home" },
            onDisconnect = { handleDisconnect() },
        )
    }
}

