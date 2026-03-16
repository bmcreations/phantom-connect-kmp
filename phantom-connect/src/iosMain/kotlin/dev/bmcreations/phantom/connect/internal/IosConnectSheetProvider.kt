@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package dev.bmcreations.phantom.connect.internal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import dev.bmcreations.phantom.connect.AuthProvider
import dev.bmcreations.phantom.connect.ConnectResult
import dev.bmcreations.phantom.connect.ConnectSheetTheme
import dev.bmcreations.phantom.connect.PhantomSession
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.UIKit.*
import kotlin.math.roundToInt

private const val SCRIM_ALPHA = 0.4f
private const val DISMISS_THRESHOLD = 150f
private val SHEET_CORNER_RADIUS = 20.dp

internal class IosConnectSheetProvider : ConnectSheetProvider {

    override suspend fun show(
        theme: ConnectSheetTheme,
        session: PhantomSession?,
        onConnect: suspend (AuthProvider) -> ConnectResult,
        onDisconnect: (suspend () -> Unit)?,
    ): ConnectResult {
        SdkLogger.debug("ConnectSheet", "Showing iOS connect sheet")
        return withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<ConnectResult>()

            val keyWindow = findKeyWindow() ?: run {
                return@withContext ConnectResult.Cancelled("No key window")
            }

            var dismissed = false
            var overlayWindow: UIWindow? = null

            fun complete(result: ConnectResult) {
                if (!dismissed) {
                    dismissed = true
                    overlayWindow?.let { w ->
                        w.setHidden(true)
                        w.rootViewController = null
                    }
                    overlayWindow = null
                    deferred.complete(result)
                }
            }

            val errorState = mutableStateOf<String?>(null)
            val loadingProviderState = mutableStateOf<AuthProvider?>(null)
            val scope = CoroutineScope(Dispatchers.Main)

            val vc = ComposeUIViewController {
                val error by remember { errorState }
                val loadingProvider by remember { loadingProviderState }

                CupertinoSheet(
                    theme = theme,
                    onDismiss = {
                        val err = errorState.value
                        complete(
                            if (err != null) ConnectResult.Error(Exception(err))
                            else ConnectResult.Cancelled("User dismissed connect sheet")
                        )
                    },
                ) { onAnimatedDismiss ->
                    PhantomConnectSheetContent(
                        theme = theme,
                        session = session,
                        error = error,
                        loadingProvider = loadingProvider,
                        onDisconnect = {
                            scope.launch {
                                onDisconnect?.invoke()
                                complete(ConnectResult.Cancelled("User disconnected"))
                            }
                        },
                        onProviderSelected = { provider ->
                            if (loadingProviderState.value == null) {
                                loadingProviderState.value = provider
                                errorState.value = null
                                scope.launch {
                                    when (val result = onConnect(provider)) {
                                        is ConnectResult.Success -> complete(result)
                                        is ConnectResult.Cancelled -> {
                                            errorState.value = "Authentication error: ${result.reason ?: "User cancelled authentication"}"
                                            loadingProviderState.value = null
                                        }
                                        is ConnectResult.Error -> {
                                            errorState.value = "Authentication error: ${result.cause.message ?: "Unknown error"}"
                                            loadingProviderState.value = null
                                        }
                                    }
                                }
                            }
                        },
                        onDismiss = {
                            onAnimatedDismiss()
                        },
                    )
                }
            }

            // Create a transparent overlay window above the app
            val windowScene = keyWindow.windowScene
            val overlay = UIWindow(windowScene = windowScene!!)
            overlay.setFrame(keyWindow.bounds)
            overlay.windowLevel = UIWindowLevelNormal + 1
            overlay.backgroundColor = UIColor.clearColor
            overlay.setOpaque(false)
            overlay.rootViewController = vc

            // Cancel bottom safe area so Compose fills to the actual screen edge.
            // This lets our uniform edgePadding produce truly concentric corners.
            val bottomInset = keyWindow.safeAreaInsets.useContents { bottom }
            vc.additionalSafeAreaInsets = UIEdgeInsetsMake(0.0, 0.0, -bottomInset, 0.0)

            vc.view.backgroundColor = UIColor.clearColor
            vc.view.setOpaque(false)
            // Make all subviews transparent too (Compose hosting view)
            vc.view.subviews.forEach { subview ->
                (subview as? UIView)?.let {
                    it.backgroundColor = UIColor.clearColor
                    it.setOpaque(false)
                }
            }
            overlay.makeKeyAndVisible()
            // Re-check after makeKeyAndVisible in case views were added
            vc.view.subviews.forEach { subview ->
                (subview as? UIView)?.let {
                    it.backgroundColor = UIColor.clearColor
                    it.setOpaque(false)
                }
            }

            overlayWindow = overlay

            deferred.await()
        }
    }

    private fun findKeyWindow(): UIWindow? {
        @Suppress("UNCHECKED_CAST")
        val scenes = UIApplication.sharedApplication.connectedScenes as Set<*>
        return scenes.firstNotNullOfOrNull { scene ->
            (scene as? UIWindowScene)?.windows
                ?.filterIsInstance<UIWindow>()
                ?.firstOrNull { it.isKeyWindow() }
        }
    }
}

/**
 * Pure Compose iOS-style bottom sheet that wraps its content height.
 */
@Composable
private fun CupertinoSheet(
    theme: ConnectSheetTheme,
    onDismiss: () -> Unit,
    content: @Composable (onAnimatedDismiss: () -> Unit) -> Unit,
) {
    val sheetBackground = Color(theme.sheetBackground)
    var sheetContentHeight by remember { mutableStateOf(0f) }
    val sheetOffset = remember { Animatable(10000f) }
    val scope = rememberCoroutineScope()

    // iOS 26+ floating sheet with concentric corners.
    // The VC's additionalSafeAreaInsets cancels bottom safe area so Compose fills to screen edge.
    // R_inner = R_outer - Padding for concentric corners.
    val edgePadding = if (isIos26) 8.dp else 0.dp
    val bottomPadding = if (isIos26) 8.dp else 0.dp
    val sheetCornerRadius = if (isIos26) (displayCornerRadius.toInt() - 8).dp else SHEET_CORNER_RADIUS

    // Animate in when content is measured
    LaunchedEffect(sheetContentHeight) {
        if (sheetContentHeight > 0f && sheetOffset.value > sheetContentHeight) {
            sheetOffset.snapTo(sheetContentHeight)
            sheetOffset.animateTo(0f, tween(350))
        }
    }

    // Derive scrim alpha from sheet position
    val progress = if (sheetContentHeight > 0f) {
        (sheetOffset.value / sheetContentHeight).coerceIn(0f, 1f)
    } else 1f
    val scrimAlpha = SCRIM_ALPHA * (1f - progress)

    // Animated dismiss: slide sheet out, then call onDismiss
    val animatedDismiss: () -> Unit = {
        scope.launch {
            if (sheetContentHeight > 0f) {
                sheetOffset.animateTo(sheetContentHeight, tween(250))
            }
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    if (scrimAlpha > 0f) {
                        drawRect(Color.Black.copy(alpha = scrimAlpha))
                    }
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = animatedDismiss,
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isIos26) {
                        Modifier.padding(
                            start = edgePadding,
                            end = edgePadding,
                            bottom = bottomPadding,
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .offset { IntOffset(0, sheetOffset.value.roundToInt()) }
                    .onGloballyPositioned { coords ->
                        val h = coords.size.height.toFloat()
                        if (h > 0f && h != sheetContentHeight) {
                            sheetContentHeight = h
                        }
                    }
                    .clip(if (isIos26) RoundedCornerShape(sheetCornerRadius)
                          else RoundedCornerShape(topStart = SHEET_CORNER_RADIUS, topEnd = SHEET_CORNER_RADIUS))
                    .background(sheetBackground)
                    .pointerInput(onDismiss) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (sheetOffset.value > DISMISS_THRESHOLD) {
                                        sheetOffset.animateTo(sheetContentHeight, tween(250))
                                        onDismiss() // already at bottom, no need for animatedDismiss
                                    } else {
                                        sheetOffset.animateTo(0f, tween(250))
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { sheetOffset.animateTo(0f, tween(250)) }
                            },
                            onVerticalDrag = { _, dragAmount ->
                                scope.launch {
                                    val newOffset = (sheetOffset.value + dragAmount).coerceAtLeast(0f)
                                    sheetOffset.snapTo(newOffset)
                                }
                            },
                        )
                    },
            ) {
                // Drag handle
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .size(width = 36.dp, height = 5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color.White.copy(alpha = 0.3f)),
                    )
                }

                content(animatedDismiss)
            }
        }
    }
}
