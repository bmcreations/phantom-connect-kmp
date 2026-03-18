package dev.bmcreations.phantom.connect.sample.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bmcreations.phantom.connect.ConnectSheetTheme
import dev.bmcreations.phantom.connect.PhantomSdk
import dev.bmcreations.phantom.connect.PhantomSession
import dev.bmcreations.phantom.connect.sample.components.SectionCard
import dev.bmcreations.phantom.connect.sample.components.WalletInfoSection
import dev.bmcreations.phantom.connect.sample.theme.PhantomIndigo
import dev.bmcreations.phantom.connect.sample.theme.PhantomPurple
import dev.bmcreations.phantom.connect.sample.theme.PhantomRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    sdk: PhantomSdk,
    session: PhantomSession?,
    error: String?,
    onConnect: () -> Unit,
    onConnectGoogle: () -> Unit,
    onConnectPhantom: () -> Unit,
    onOpenWallet: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val isConnected = session != null
    var selectedTheme by remember { mutableStateOf("Dark") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phantom KMP SDK Demo") },
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
            // Title
            Text(
                text = "Phantom Connect SDK",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Demo Application",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            // Connection Status card
            SectionCard {
                Text(
                    text = "Connection Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Color(0xFF4CAF50) else PhantomRed)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) "Connected" else "Disconnected",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (session != null) {
                    Spacer(Modifier.height(12.dp))
                    WalletInfoSection(session)
                }
            }

            // Modal Theme card
            SectionCard {
                Text(
                    text = "Modal Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Switch between different themes for the modal:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Dark", "Light", "Custom").forEach { label ->
                        val isSelected = selectedTheme == label
                        if (isSelected) {
                            Button(
                                onClick = {},
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PhantomPurple,
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    selectedTheme = label
                                    sdk.theme = when (label) {
                                        "Light" -> ConnectSheetTheme.Light
                                        "Custom" -> ConnectSheetTheme.Custom(
                                            sheetBackground = 0xFFFF6B35L,
                                            optionBackground = 0xFFFF8A5CL,
                                            accentColor = 0xFFFFFFFF,
                                            textPrimary = 0xFFFFFFFFL,
                                            textSecondary = 0xFFFFD4C2L,
                                        )
                                        else -> ConnectSheetTheme.Dark
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            if (isConnected) {
                // Wallet Actions card (connected)
                SectionCard {
                    Text(
                        text = "Wallet Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PhantomPurple),
                    ) {
                        Text("Open Wallet Modal", color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onOpenWallet,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PhantomPurple),
                    ) {
                        Text("Open Wallet Operations", color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PhantomRed),
                    ) {
                        Text("Disconnect Wallet", color = Color.White)
                    }
                }
            } else {
                // Connect Wallet card (disconnected)
                SectionCard {
                    Text(
                        text = "Connect Wallet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Connect your Phantom wallet using various authentication methods:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PhantomPurple),
                    ) {
                        Text("Open Connect Modal", color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onConnectGoogle,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PhantomRed),
                    ) {
                        Text("Connect with Google", color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onConnectPhantom,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PhantomIndigo),
                    ) {
                        Text("Connect with Phantom App", color = Color.White)
                    }
                }
            }

            // Error card (only shown when there's an error)
            if (error != null) {
                SectionCard {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = PhantomRed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                PhantomRed.copy(alpha = 0.1f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(10.dp),
                    )
                }
            }

            // About card
            SectionCard {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "This demo app showcases the Phantom Connect KMP SDK integration. " +
                            "It demonstrates wallet connection, authentication flows, message " +
                            "signing, and transaction handling in a Kotlin Multiplatform environment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
