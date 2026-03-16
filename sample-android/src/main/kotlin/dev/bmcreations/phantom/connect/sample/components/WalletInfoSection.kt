package dev.bmcreations.phantom.connect.sample.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bmcreations.phantom.connect.PhantomSession
import dev.bmcreations.phantom.connect.sample.theme.PhantomPurple

@Composable
internal fun WalletInfoSection(session: PhantomSession) {
    Text(
        text = "Wallet ID:",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
    )
    SelectionContainer {
        Text(
            text = session.walletId,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (session.addresses.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Addresses:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
        session.addresses.forEach { addr ->
            Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
                Text(
                    text = "${addr.chain.id.uppercase()}:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = PhantomPurple,
                )
                SelectionContainer {
                    Text(
                        text = addr.address,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
