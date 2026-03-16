package dev.bmcreations.phantom.connect.internal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bmcreations.phantom.connect.AuthProvider
import dev.bmcreations.phantom.connect.ConnectSheetTheme
import dev.bmcreations.phantom.connect.PhantomSession


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhantomConnectSheet(
    theme: ConnectSheetTheme = ConnectSheetTheme.Dark,
    session: PhantomSession? = null,
    error: String? = null,
    loadingProvider: AuthProvider? = null,
    onProviderSelected: (AuthProvider) -> Unit,
    onDisconnect: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetBackground = Color(theme.sheetBackground)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = null,
    ) {
        PhantomConnectSheetContent(
            theme = theme,
            session = session,
            error = error,
            loadingProvider = loadingProvider,
            onProviderSelected = onProviderSelected,
            onDisconnect = onDisconnect,
            onDismiss = onDismiss,
        )
    }
}

/**
 * The sheet content without the ModalBottomSheet wrapper.
 * Used directly on iOS where the native UISheetPresentationController provides the sheet chrome.
 */
@Composable
internal fun PhantomConnectSheetContent(
    theme: ConnectSheetTheme = ConnectSheetTheme.Dark,
    session: PhantomSession? = null,
    error: String? = null,
    loadingProvider: AuthProvider? = null,
    onProviderSelected: (AuthProvider) -> Unit,
    onDisconnect: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetBackground = Color(theme.sheetBackground)
    val optionBackground = Color(theme.optionBackground)
    val accentColor = Color(theme.accentColor)
    val textPrimary = Color(theme.textPrimary)
    val textSecondary = Color(theme.textSecondary)

    if (session != null) {
        ConnectedContent(
            session = session,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onDisconnect = onDisconnect,
            onDismiss = onDismiss,
        )
    } else {
        LoginContent(
            error = error,
            loadingProvider = loadingProvider,
            sheetBackground = sheetBackground,
            optionBackground = optionBackground,
            accentColor = accentColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onProviderSelected = onProviderSelected,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ConnectedContent(
    session: PhantomSession,
    textPrimary: Color,
    textSecondary: Color,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 28.dp),
    ) {
        // Header: "Wallet" + X button
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Wallet",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = textSecondary,
                modifier = Modifier.align(Alignment.Center),
            )
            Text(
                text = "\u2715",
                fontSize = 18.sp,
                color = textSecondary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(4.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        // Addresses
        session.addresses.forEach { addr ->
            Text(
                text = addr.chain.id.uppercase(),
                fontSize = 12.sp,
                color = textSecondary,
            )
            Spacer(Modifier.height(2.dp))
            SelectionContainer {
                Text(
                    text = addr.address,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = textPrimary,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))

        // Disconnect button
        Button(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF333333),
                contentColor = Color.White,
            ),
        ) {
            Text("Disconnect", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LoginContent(
    error: String?,
    loadingProvider: AuthProvider?,
    sheetBackground: Color,
    optionBackground: Color,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onProviderSelected: (AuthProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header: "Login or Sign Up" + X button
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Login or Sign Up",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimary,
                modifier = Modifier.align(Alignment.Center),
            )
            Text(
                text = "\u2715",
                fontSize = 18.sp,
                color = textSecondary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(4.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Phantom logo
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(accentColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "\uD83D\uDC7B", fontSize = 28.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Error banner
        if (error != null) {
            Text(
                text = error,
                fontSize = 13.sp,
                color = Color(0xFFFF4444),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFFFF4444).copy(alpha = 0.12f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(12.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        val loading = loadingProvider != null

        // Continue with Google
        ConnectOption(
            label = "Continue with Google",
            optionBackground = optionBackground,
            accentColor = accentColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            enabled = !loading,
            loading = loadingProvider == AuthProvider.Google,
            onClick = { onProviderSelected(AuthProvider.Google) },
        ) {
            GoogleIcon(
                modifier = Modifier.size(14.dp),
                color = textPrimary.copy(alpha = if (!loading) 1f else 0.5f),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Continue with Apple
        ConnectOption(
            label = "Continue with Apple",
            optionBackground = optionBackground,
            accentColor = accentColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            enabled = !loading,
            loading = loadingProvider == AuthProvider.Apple,
            onClick = { onProviderSelected(AuthProvider.Apple) },
        ) {
            AppleIcon(
                modifier = Modifier.size(14.dp),
                color = textPrimary.copy(alpha = if (!loading) 1f else 0.5f),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Powered by Phantom footer
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Powered by", fontSize = 12.sp, color = textSecondary)
            Spacer(modifier = Modifier.width(4.dp))
            PhantomGhostIcon(
                modifier = Modifier.size(16.dp),
                color = textSecondary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Phantom",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textSecondary,
            )
        }
    }
}

@Composable
private fun ConnectOption(
    label: String,
    optionBackground: Color,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val alpha = if (enabled || loading) 1f else 0.5f
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        color = optionBackground.copy(alpha = alpha),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimary.copy(alpha = alpha),
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                CircularProgressIndicator(
                    color = accentColor,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = "\u203A",
                    fontSize = 20.sp,
                    color = textSecondary.copy(alpha = alpha),
                )
            }
        }
    }
}

/**
 * Official Phantom ghost logomark drawn from the press kit SVG path.
 * ViewBox: 0 0 593 493
 */
@Composable
private fun PhantomGhostIcon(
    modifier: Modifier = Modifier,
    color: Color,
) {
    Canvas(modifier = modifier) {
        val sx = size.width / 593f
        val sy = size.height / 493f

        val path = Path().apply {
            // Body outline
            moveTo(70.0546f * sx, 493f * sy)
            cubicTo(145.604f * sx, 493f * sy, 202.38f * sx, 427.297f * sy, 236.263f * sx, 375.378f * sy)
            cubicTo(232.142f * sx, 386.865f * sy, 229.852f * sx, 398.351f * sy, 229.852f * sx, 409.378f * sy)
            cubicTo(229.852f * sx, 439.703f * sy, 247.252f * sx, 461.297f * sy, 281.592f * sx, 461.297f * sy)
            cubicTo(328.753f * sx, 461.297f * sy, 379.119f * sx, 419.946f * sy, 405.218f * sx, 375.378f * sy)
            cubicTo(403.386f * sx, 381.811f * sy, 402.471f * sx, 387.784f * sy, 402.471f * sx, 393.297f * sy)
            cubicTo(402.471f * sx, 414.432f * sy, 414.375f * sx, 427.757f * sy, 438.643f * sx, 427.757f * sy)
            cubicTo(515.108f * sx, 427.757f * sy, 592.03f * sx, 292.216f * sy, 592.03f * sx, 173.676f * sy)
            cubicTo(592.03f * sx, 81.3243f * sy, 545.327f * sx, 0f * sy, 428.112f * sx, 0f * sy)
            cubicTo(222.069f * sx, 0f * sy, 0f * sx, 251.784f * sy, 0f * sx, 414.432f * sy)
            cubicTo(0f * sx, 478.297f * sy, 34.3405f * sx, 493f * sy, 70.0546f * sx, 493f * sy)
            close()

            // Left eye
            moveTo(357.141f * sx, 163.568f * sy)
            cubicTo(357.141f * sx, 140.595f * sy, 369.962f * sx, 124.514f * sy, 388.734f * sx, 124.514f * sy)
            cubicTo(407.049f * sx, 124.514f * sy, 419.87f * sx, 140.595f * sy, 419.87f * sx, 163.568f * sy)
            cubicTo(419.87f * sx, 186.541f * sy, 407.049f * sx, 203.081f * sy, 388.734f * sx, 203.081f * sy)
            cubicTo(369.962f * sx, 203.081f * sy, 357.141f * sx, 186.541f * sy, 357.141f * sx, 163.568f * sy)
            close()

            // Right eye
            moveTo(455.126f * sx, 163.568f * sy)
            cubicTo(455.126f * sx, 140.595f * sy, 467.947f * sx, 124.514f * sy, 486.719f * sx, 124.514f * sy)
            cubicTo(505.034f * sx, 124.514f * sy, 517.855f * sx, 140.595f * sy, 517.855f * sx, 163.568f * sy)
            cubicTo(517.855f * sx, 186.541f * sy, 505.034f * sx, 203.081f * sy, 486.719f * sx, 203.081f * sy)
            cubicTo(467.947f * sx, 203.081f * sy, 455.126f * sx, 186.541f * sy, 455.126f * sx, 163.568f * sy)
            close()
        }

        drawPath(path, color, style = Fill)
    }
}

/**
 * Apple logo drawn as a Canvas vector path.
 * Based on the standard Apple logo SVG. ViewBox: 0 0 814 1000.
 */
@Composable
private fun AppleIcon(
    modifier: Modifier = Modifier,
    color: Color,
) {
    Canvas(modifier = modifier) {
        val sx = size.width / 814f
        val sy = size.height / 1000f

        val path = Path().apply {
            // Apple body
            moveTo(788.1f * sx, 340.9f * sy)
            cubicTo(782.3f * sx, 344.2f * sy, 681.3f * sx, 403.5f * sy, 681.3f * sx, 524.8f * sy)
            cubicTo(681.3f * sx, 664.8f * sy, 806.3f * sx, 716.3f * sy, 810.0f * sx, 717.5f * sy)
            cubicTo(809.4f * sx, 720.2f * sy, 790.5f * sx, 782.5f * sy, 748.1f * sx, 846.1f * sy)
            cubicTo(710.5f * sx, 902.4f * sy, 671.1f * sx, 958.7f * sy, 612.5f * sx, 958.7f * sy)
            cubicTo(553.9f * sx, 958.7f * sy, 537.5f * sx, 924.7f * sy, 469.5f * sx, 924.7f * sy)
            cubicTo(401.5f * sx, 924.7f * sy, 377.9f * sx, 960.0f * sy, 323.5f * sx, 960.0f * sy)
            cubicTo(269.1f * sx, 960.0f * sy, 231.3f * sx, 907.3f * sy, 188.1f * sx, 843.5f * sy)
            cubicTo(138.3f * sx, 768.9f * sy, 98.1f * sx, 660.3f * sy, 98.1f * sx, 557.5f * sy)
            cubicTo(98.1f * sx, 396.7f * sy, 201.7f * sx, 311.5f * sy, 304.1f * sx, 311.5f * sy)
            cubicTo(360.7f * sx, 311.5f * sy, 407.7f * sx, 349.3f * sy, 443.9f * sx, 349.3f * sy)
            cubicTo(478.3f * sx, 349.3f * sy, 531.7f * sx, 308.0f * sy, 596.5f * sx, 308.0f * sy)
            cubicTo(621.3f * sx, 308.0f * sy, 722.3f * sx, 311.5f * sy, 788.1f * sx, 340.9f * sy)
            close()

            // Leaf
            moveTo(554.1f * sx, 203.7f * sy)
            cubicTo(581.9f * sx, 170.3f * sy, 601.3f * sx, 124.3f * sy, 601.3f * sx, 78.3f * sy)
            cubicTo(601.3f * sx, 71.7f * sy, 600.7f * sx, 65.1f * sy, 599.5f * sx, 59.7f * sy)
            cubicTo(554.7f * sx, 61.5f * sy, 500.9f * sx, 89.7f * sy, 469.5f * sx, 127.5f * sy)
            cubicTo(444.5f * sx, 157.1f * sy, 421.1f * sx, 203.1f * sy, 421.1f * sx, 249.7f * sy)
            cubicTo(421.1f * sx, 257.0f * sy, 422.3f * sx, 264.2f * sy, 422.9f * sx, 266.6f * sy)
            cubicTo(425.9f * sx, 267.2f * sy, 430.7f * sx, 268.0f * sy, 435.5f * sx, 268.0f * sy)
            cubicTo(476.3f * sx, 268.0f * sy, 524.5f * sx, 241.2f * sy, 554.1f * sx, 203.7f * sy)
            close()
        }

        drawPath(path, color, style = Fill)
    }
}

/**
 * Google "G" logo drawn as a Canvas vector path.
 * Based on the Google logo SVG. ViewBox: 0 0 48 48.
 */
@Composable
private fun GoogleIcon(
    modifier: Modifier = Modifier,
    color: Color,
) {
    Canvas(modifier = modifier) {
        val sx = size.width / 48f
        val sy = size.height / 48f

        val path = Path().apply {
            moveTo(44.5f * sx, 20f * sy)
            lineTo(24f * sx, 20f * sy)
            lineTo(24f * sx, 28.5f * sy)
            lineTo(35.8f * sx, 28.5f * sy)
            cubicTo(34.8f * sx, 33.5f * sy, 30.1f * sx, 38f * sy, 24f * sx, 38f * sy)
            cubicTo(16.3f * sx, 38f * sy, 10f * sx, 31.7f * sy, 10f * sx, 24f * sy)
            cubicTo(10f * sx, 16.3f * sy, 16.3f * sx, 10f * sy, 24f * sx, 10f * sy)
            cubicTo(27.4f * sx, 10f * sy, 30.5f * sx, 11.2f * sy, 33f * sx, 13.2f * sy)
            lineTo(39f * sx, 7.2f * sy)
            cubicTo(35.2f * sx, 3.9f * sy, 29.9f * sx, 2f * sy, 24f * sx, 2f * sy)
            cubicTo(11.8f * sx, 2f * sy, 2f * sx, 11.8f * sy, 2f * sx, 24f * sy)
            cubicTo(2f * sx, 36.2f * sy, 11.8f * sx, 46f * sy, 24f * sx, 46f * sy)
            cubicTo(36.2f * sx, 46f * sy, 44f * sx, 37f * sy, 44f * sx, 24.5f * sy)
            cubicTo(44f * sx, 23f * sy, 44.5f * sx, 21.5f * sy, 44.5f * sx, 20f * sy)
            close()
        }

        drawPath(path, color, style = Fill)
    }
}
