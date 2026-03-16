package dev.bmcreations.phantom.connect.sample.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.bmcreations.phantom.connect.sample.theme.PhantomExplorerGreen
import dev.bmcreations.phantom.connect.sample.theme.PhantomRed

private val SuccessGreen = Color(0xFF4CAF50)

@Composable
internal fun ResultDisplay(
    result: String?,
    error: String?,
    label: String,
    solscanHash: String? = null,
) {
    if (result != null) {
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    SuccessGreen.copy(alpha = 0.1f),
                    RoundedCornerShape(6.dp),
                )
                .padding(10.dp),
        ) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = SuccessGreen,
            )
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (solscanHash != null) {
                Spacer(Modifier.height(8.dp))
                val uriHandler = LocalUriHandler.current
                OutlinedButton(
                    onClick = {
                        uriHandler.openUri("https://solscan.io/tx/$solscanHash")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PhantomExplorerGreen,
                    ),
                    border = BorderStroke(1.dp, PhantomExplorerGreen),
                ) {
                    Text("View on Solscan")
                }
            }
        }
    }
    if (error != null) {
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
