package dev.bmcreations.phantom.connect.sample.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bmcreations.phantom.connect.Chain
import dev.bmcreations.phantom.connect.PhantomSdk
import dev.bmcreations.phantom.connect.PhantomSession
import dev.bmcreations.phantom.connect.sample.LAMPORTS_PER_SOL
import dev.bmcreations.phantom.connect.sample.components.ResultDisplay
import dev.bmcreations.phantom.connect.sample.components.SectionCard
import dev.bmcreations.phantom.connect.sample.components.WalletInfoSection
import dev.bmcreations.phantom.connect.sample.theme.PhantomBlue
import dev.bmcreations.phantom.connect.sample.theme.PhantomIndigo
import dev.bmcreations.phantom.connect.sample.theme.PhantomPurple
import dev.bmcreations.phantom.connect.sample.theme.PhantomRed
import dev.bmcreations.phantom.connect.sample.utils.buildSelfTransferTransaction
import dev.bmcreations.phantom.connect.sample.utils.getSolBalance
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WalletOperationsScreen(
    sdk: PhantomSdk,
    session: PhantomSession,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf("Hello from Phantom SDK!") }
    var signatureResult by remember { mutableStateOf<String?>(null) }
    var signatureError by remember { mutableStateOf<String?>(null) }
    var txResult by remember { mutableStateOf<String?>(null) }
    var txError by remember { mutableStateOf<String?>(null) }
    var signing by remember { mutableStateOf(false) }
    var sendingTx by remember { mutableStateOf(false) }
    var showSendConfirmation by remember { mutableStateOf(false) }

    // SOL balance
    var balanceSol by remember { mutableStateOf<Double?>(null) }
    var balanceLoading by remember { mutableStateOf(true) }
    var balanceError by remember { mutableStateOf<String?>(null) }
    val solAddress = session.address(Chain.Solana)

    fun fetchBalance() {
        if (solAddress == null) {
            balanceLoading = false
            balanceError = "No Solana address found in session"
            return
        }
        balanceLoading = true
        balanceError = null
        scope.launch {
            try {
                val lamports = getSolBalance(solAddress)
                balanceSol = lamports / LAMPORTS_PER_SOL
            } catch (e: Exception) {
                balanceError = e.message
            } finally {
                balanceLoading = false
            }
        }
    }

    LaunchedEffect(solAddress) { fetchBalance() }

    val hasBalance = balanceSol != null && balanceSol!! > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallet Operations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PhantomPurple,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Wallet Information card
            SectionCard {
                Text(
                    text = "Wallet Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                WalletInfoSection(session)
            }

            // SOL Balance card
            SectionCard {
                Text(
                    text = "SOL Balance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = when {
                            balanceLoading -> "Loading..."
                            balanceError != null -> "Error"
                            balanceSol != null -> "%.4f SOL".format(balanceSol)
                            else -> "--"
                        },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = PhantomBlue,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { fetchBalance() },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Refresh")
                    }
                }
                if (balanceError != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = balanceError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = PhantomRed,
                    )
                }
            }

            // Sign Message card
            SectionCard {
                Text(
                    text = "Sign Message",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Enter a message to sign with your wallet:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        signing = true
                        signatureResult = null
                        signatureError = null
                        scope.launch {
                            try {
                                signatureResult = sdk.solana.signMessage(messageText)
                            } catch (e: Exception) {
                                signatureError = e.message
                            } finally {
                                signing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PhantomPurple),
                    enabled = !signing && messageText.isNotBlank(),
                ) {
                    Text(
                        if (signing) "Signing..." else "Sign Message",
                        color = Color.White,
                    )
                }
                ResultDisplay(result = signatureResult, error = signatureError, label = "Signature")
            }

            // Sign Transaction card
            SectionCard {
                Text(
                    text = "Sign Transaction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "This demonstrates transaction signing capabilities:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showSendConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !sendingTx && hasBalance,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PhantomIndigo,
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (!sendingTx && hasBalance) PhantomIndigo else Color.Gray,
                    ),
                ) {
                    Text(
                        when {
                            sendingTx -> "Sending..."
                            !hasBalance -> "Insufficient Balance"
                            else -> "Demo Transaction Signing"
                        },
                    )
                }
                ResultDisplay(
                    result = txResult,
                    error = txError,
                    label = "Transaction",
                    solscanHash = txResult,
                )
            }

            // Wallet Actions card
            SectionCard {
                Text(
                    text = "Wallet Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PhantomRed),
                ) {
                    Text("Disconnect Wallet", color = Color.White)
                }
            }
        }
    }

    if (showSendConfirmation) {
        AlertDialog(
            onDismissRequest = { showSendConfirmation = false },
            title = { Text("Send Transaction") },
            text = {
                Text("This will create a small self-transfer transaction (0.000001 SOL) to demonstrate signing and sending.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showSendConfirmation = false
                    sendingTx = true
                    txResult = null
                    txError = null
                    scope.launch {
                        try {
                            txResult = sdk.solana.signAndSendTransaction(
                                buildSelfTransferTransaction(solAddress!!)
                            )
                            // Refresh balance after successful transaction
                            delay(2.seconds)
                            fetchBalance()
                        } catch (e: Exception) {
                            txError = e.message
                        } finally {
                            sendingTx = false
                        }
                    }
                }) {
                    Text("Send Transaction")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
